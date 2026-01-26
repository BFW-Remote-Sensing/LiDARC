import argparse
import json
import logging
import shutil
import signal
import sys
import tempfile
import time
import math

from typing import List, Dict, Any, Optional, Tuple, Counter
from jsonschema.exceptions import ValidationError
from jsonschema.validators import validate
from messaging.message_model import BaseMessage
from messaging.rabbit_config import get_rabbitmq_config
from messaging.rabbit_connect import create_rabbit_con_and_return_channel
from messaging.result_publisher import ResultPublisher
import util.file_handler as file_handler
from pika.exceptions import ChannelWrongStateError, ReentrancyError, StreamLostError
from requests import HTTPError
from schemas.comparison import schema as comparison_schema
from redis_cache import get_redis_cache

rabbitConfig = get_rabbitmq_config()



def handle_sigterm(signum, frame):
    logging.info("SIGTERM received")
    sys.exit(0)


signal.signal(signal.SIGTERM, handle_sigterm)


def process_req(ch, method, properties, body):
    logging.info("Received comparison results payload")
    publisher = ResultPublisher(ch)
    start_time = time.time()
    request = json.loads(body)

    comparison_id = request["comparisonId"]
    chunking_size = request["chunkingSize"]

    logging.info(f"Processing chunking comparison result for comparison ID: {comparison_id} with chunking size: {chunking_size}")

    file_storage_details = request["file"]
    temp_dir = tempfile.mkdtemp()
    comparison_result_path = ""
    try:
        comparison_result_path = file_handler.fetch_file(file_storage_details, dest_dir=temp_dir)
    except HTTPError as e:
        logging.warning("Couldn't download file from: {}, error: {}".format(file_storage_details, e))
        publisher.publish_chunking_comparison_result(BaseMessage(type="chunking-comparison-result",
                                                                 status="error",
                                                                 job_id="",
                                                                 payload=mk_error_msg(
                                                                     error_msg="Couldn't download file from: {}, chunking-comparison job cancelled".format(
                                                                         file_storage_details))))
    with open(comparison_result_path, "r") as f:
        comparison_result = json.load(f)


    valid = True
    # TODO validate on fetched data

    """
        if ("payload" in request and not validate_comparison_result(comparison_result)) or not validate_comparison_result(comparison_result):
        valid = False
    if not valid:
        logging.warning("The chunking comparison result job is cancelled because of an Validation Error ")
        publisher.publish_chunking_comparison_result(BaseMessage(type="chunking_comparison_result",
                                                           status="error",
                                                           job_id="",
                                                           payload=mk_error_msg(
                                                               error_msg="The chunking comparison result job is cancelled because job request is invalid")))
        return
"""

    # Process request
    cells = comparison_result["cells"]
    statistics = comparison_result["statistics"]
    group_mapping = comparison_result["group_mapping"]



    if chunking_size > len(cells):
        logging.warning("Chunking comparison result job is cancelled because chunking size exceeds the number of cells")
        publisher.publish_chunking_comparison_result(BaseMessage(type="chunking_comparison_result",
                                                                 status="error",
                                                                 job_id="",
                                                                 payload=mk_error_msg(
                                                                     error_msg=f"The Chunking comparison result job is cancelled because chunking of: {chunking_size} cells exceeds the total number of cells:  {len(cells)}")))
        return

    # Create a 2D matrix from comparison results list
    cells_matrix = to_2d(cells)
    debug_print_matrix(cells_matrix)
    if chunking_size != 1:
         # Chunk newly created 2D Matrix to blocks of size chunking_size
        cells_matrix = chunking_func(cells_matrix, chunking_size)
        debug_print_matrix(cells_matrix)

    message = build_response_message(comparison_id, cells_matrix, statistics, chunking_size, group_mapping)

    # Write result directly to Redis cache (keyed by comparison_id and chunk_size)
    redis_cache = get_redis_cache()
    cache_success = redis_cache.save(comparison_id, chunking_size, message)

    if cache_success:
        # Send lightweight notification to backend (no payload - result is in Redis)
        notification = {
            "comparisonId": comparison_id,
            "chunkSize": chunking_size,
            "cached": True
        }
        publisher.publish_chunking_comparison_result(BaseMessage(type="chunking_comparison_result",
                                                                 status="success",
                                                                 job_id="",
                                                                 payload=notification))
        logging.info(f"Chunking result for comparison {comparison_id} (chunkSize={chunking_size}) saved to Redis and notification sent")
    else:
        # Fallback: send full payload via RabbitMQ if Redis fails
        logging.warning(f"Redis save failed for comparison {comparison_id}, falling back to RabbitMQ payload")
        publisher.publish_chunking_comparison_result(BaseMessage(type="chunking_comparison_result",
                                                                 status="success",
                                                                 job_id="",
                                                                 payload=message))

def build_response_message(comparison_id, chunked_matrix, statistics, chunking_size, group_mapping):
    return {
        "comparisonId": comparison_id,
        "chunkingSize": chunking_size,
        "chunked_cells": chunked_matrix,
        "statistics": statistics,
        "group_mapping": group_mapping
    }





Cell = Dict[str, Any]
Matrix = List[List[Cell]]

#
# def chunk_cells_average_veg_height(
#     cells_matrix: Matrix,
#     chunk_size: int,
# ) -> Matrix:
#     """
#     Downsample a 2D grid of cells by grouping cells into blocks.
#
#     - Block side length is sqrt(chunk_size)
#     - chunk_size must be >= 1
#     - For each block:
#         - avg veg_height is computed over *existing* cells only (missing cells are ignored).
#         - bounding box is computed from existing cells only.
#     - Incomplete edge blocks are discarded.
#     - Blocks with no existing cells are discarded.
#
#
#     Each output cell represents one aggregated block.
#
#     Input cell format example:
#     {
#         "x0": float,
#         "x1": float,
#         "y0": float,
#         "y1": float,
#         "veg_height_max_a": float,
#         "veg_height_max_b": float,
#         "delta_z": float
#     }
#
#     Output cell format:
#     {
#         "x0": first cell's x0 in the block,
#         "x1": last cell's  x1 in the block,
#         "y0": first cell's y0 in the block,
#         "y1": last cell's  y1 in the block,
#         "veg_height_max_a": mean of all a-values in the block,
#         "veg_height_max_b": mean of all b-values in the block,
#         "delta_z": sum(b-values) - sum(a-values)
#                    (use mean_b - mean_a if needed instead)
#     }
#
#
#     """
#
#     if not cells_matrix:
#         return []
#
#     if chunk_size < 1:
#         raise ValueError("chunk_size must be >= 1.")
#
#
#     rows = len(cells_matrix)
#     if rows == 0:
#         return []
#
#     reduced: Matrix = []
#
#     logging.info("Started actual chunking in worker...")
#     # Step over block rows; discard incomplete blocks at the bottom edge
#     for br in range(0, rows, chunk_size):
#         if br + chunk_size > rows:
#             break
#
#         reduced_row: List[Cell] = []
#
#         # We cannot assume rectangular rows; we will still discard incomplete right-edge blocks,
#         # but we do so per block by checking whether *all* rows have enough columns for that block.
#         # To find how far we can go to the right, we use the minimum row length within this block-row band.
#         band_min_cols = min(len(cells_matrix[r]) for r in range(br, br + chunk_size))
#         if band_min_cols == 0:
#             # nothing usable in this band; move on
#             continue
#
#         for bc in range(0, band_min_cols, chunk_size):
#             if bc + chunk_size > band_min_cols:
#                 break  # discard incomplete right-edge blocks
#
#             sum_a = 0.0
#             sum_b = 0.0
#             count = 0
#
#             min_x0: Optional[float] = None
#             min_y0: Optional[float] = None
#             max_x1: Optional[float] = None
#             max_y1: Optional[float] = None
#
#             # Aggregate over the block; ignore missing cells (ragged rows) by bounds checks
#             for r in range(br, br + chunk_size):
#                 row = cells_matrix[r]
#                 for c in range(bc, bc + chunk_size):
#                     if c >= len(row):
#                         # Missing cell in this row at this column position -> ignore
#                         continue
#                     cell = row[c]
#                     if not cell:
#                         # ignore safely
#                         continue
#
#                     # veg_height is always filled
#                     h_a = float(cell["veg_height_max_a"])
#                     h_b = float(cell["veg_height_max_b"])
#                     sum_a += h_a
#                     sum_b += h_b
#                     count += 1
#
#                     x0 = float(cell["x0"])
#                     y0 = float(cell["y0"])
#                     x1 = float(cell["x1"])
#                     y1 = float(cell["y1"])
#
#                     min_x0 = x0 if (min_x0 is None or x0 < min_x0) else min_x0
#                     min_y0 = y0 if (min_y0 is None or y0 < min_y0) else min_y0
#                     max_x1 = x1 if (max_x1 is None or x1 > max_x1) else max_x1
#                     max_y1 = y1 if (max_y1 is None or y1 > max_y1) else max_y1
#
#             # If the entire block is empty (only holes), discard it
#             if count == 0:
#                 continue
#
#             reduced_cell: Cell = {
#                 "x0": min_x0,
#                 "y0": min_y0,
#                 "x1": max_x1,
#                 "y1": max_y1,
#                 "veg_height_max_a": sum_a / count,
#                 "veg_height_max_b": sum_b / count,
#                 "delta_z": sum_b - sum_a
#             }
#             reduced_row.append(reduced_cell)
#
#         if reduced_row:
#             reduced.append(reduced_row)
#
#     return reduced

def chunking_func(cells_matrix: Matrix, chunk_size: int) -> List[Cell]:
    if cells_matrix is None or len(cells_matrix) == 0:
        return []

    if chunk_size < 1:
        raise ValueError("chunk_size must be >= 1.")

    rows = len(cells_matrix)
    cols = max((len(r) for r in cells_matrix), default=0)

    if cols == 0:
        return []

    # 1) Grid-Parameter aus vorhandenen Zellen ableiten (Ursprung + dx/dy)
    origin_x: Optional[float] = None
    origin_y: Optional[float] = None
    dx: Optional[float] = None
    dy: Optional[float] = None


    for r in range(rows):
        row = cells_matrix[r]
        for c in range(len(row)):
            cell = row[c]
            if not cell:
                continue
            x0 = float(cell["x0"])
            y0 = float(cell["y0"])
            x1 = float(cell["x1"])
            y1 = float(cell["y1"])

            # Ursprung als globales Minimum
            origin_x = x0 if (origin_x is None or x0 < origin_x) else origin_x
            origin_y = y0 if (origin_y is None or y0 < origin_y) else origin_y

            # cell size (all cells should have same width/height)
            if dx is None:
                dx = abs(x1 - x0)
            if dy is None:
                dy = abs(y1 - y0)

    if origin_x is None or origin_y is None or dx is None or dy is None or dx == 0.0 or dy == 0.0:
        # no valid cells
        return []

    # 2) Ziel-Dimensionen (NICHT verwerfen -> ceil)
    out_rows = math.ceil(rows / chunk_size)
    out_cols = math.ceil(cols / chunk_size)

    reduced: List[List[Cell]] = []
    logging.info("Started chunking with filled blocks...")

    for out_r in range(out_rows):
        br = out_r * chunk_size  # Startrow im Original
        reduced_row: List[Cell] = []

        for out_c in range(out_cols):
            bc = out_c * chunk_size  # Startcol im Original

            sum_a = 0.0
            sum_b = 0.0
            count = 0
            sum_out_a = 0
            sum_out_b = 0
            sum_out_c7_a = 0
            sum_out_c7_b = 0

            # iterate over block (edge cases get included as far as possible)
            r_end = min(br + chunk_size, rows)
            c_end = min(bc + chunk_size, cols)

            for r in range(br, r_end):
                row = cells_matrix[r]
                for c in range(bc, c_end):
                    if c >= len(row):
                        # Ragged hole
                        continue
                    cell = row[c]
                    if not cell:
                        # explicit hole
                        continue

                    # aggregate
                    sum_a += float(cell["veg_height_max_a"])
                    sum_b += float(cell["veg_height_max_b"])
                    count += 1

                    # Aggregate outlier counts
                    sum_out_a += int(cell.get("out_a", 0) or 0)
                    sum_out_b += int(cell.get("out_b", 0) or 0)
                    sum_out_c7_a += int(cell.get("out_c7_a", 0) or 0)
                    sum_out_c7_b += int(cell.get("out_c7_b", 0) or 0)

            # Koordinaten des Blocks: aus Grid + chunk_size, unabhängig von Daten
            #    Dadurch sind ALLE reduced-Zellen gleich groß.
            block_x0 = origin_x + bc * dx
            block_y0 = origin_y + br * dy
            block_x1 = origin_x + (bc + chunk_size) * dx
            block_y1 = origin_y + (br + chunk_size) * dy

            # 5) Wenn Block leer: trotzdem Zelle erzeugen (Policy: empty_value)
            if count == 0:
                avg_a = 0.0
                avg_b = 0.0
                delta_z = 0.0
            else:
                avg_a = sum_a / count
                avg_b = sum_b / count
                    # Falls du "Delta pro Zelle" willst: (sum_b - sum_a) / count
                delta_z = (sum_b - sum_a) / count

            reduced_cell: Cell = {
                "x0": block_x0,
                "y0": block_y0,
                "x1": block_x1,
                "y1": block_y1,
                "veg_height_max_a": avg_a,
                "veg_height_max_b": avg_b,
                "delta_z": delta_z,
                # Optional hilfreich für Debug/Rendering:
                "count": count,
                "coverage": count / float(chunk_size * chunk_size),
                "out_a": sum_out_a,
                "out_b": sum_out_b,
                "out_c7_a": sum_out_c7_a,
                "out_c7_b": sum_out_c7_b,
            }
            reduced_row.append(reduced_cell)

        reduced.append(reduced_row)

    return reduced


def _mode_positive(values: List[float]) -> Optional[float]:
    """Return most common positive value (rounded for stability)."""
    vals = [v for v in values if v and v > 0]
    if not vals:
        return None
    # round to reduce floating noise
    rounded = [round(v, 9) for v in vals]
    return Counter(rounded).most_common(1)[0][0]


#
# def fill_holes_to_rect_grid(
#     cells_matrix: Matrix,
#     *,
#     empty_defaults: Optional[Dict[str, Any]] = None,
#     add_missing_y_rows: bool = True,
# ) -> Matrix:
#     """
#     Nimmt ein 2D (ragged) Array von Zellen (bereits nach y0 sortiert) und füllt fehlende
#     x-Positionen (und optional fehlende y-Reihen) auf, sodass ein rechteckiges Grid entsteht.
#
#     Erwartet pro Zelle mindestens: x0,x1,y0,y1.
#     Gibt eine Matrix zurück, in der jede Zeile die gleiche Länge hat.
#     Fehlende Zellen werden als Placeholder-Cell eingefügt (is_empty=True).
#     """
#
#     if not cells_matrix:
#         return []
#
#     # --- 1) Alle Zellen einsammeln & dx/dy und globale Bounds ableiten ---
#     xs0: List[float] = []
#     ys0: List[float] = []
#     widths: List[float] = []
#     heights: List[float] = []
#
#     min_x0 = math.inf
#     max_x1 = -math.inf
#     min_y0 = math.inf
#     max_y1 = -math.inf
#
#     flat_cells: List[Cell] = []
#     for row in cells_matrix:
#         for cell in row:
#             if not cell:
#                 continue
#             x0 = float(cell["x0"]); x1 = float(cell["x1"])
#             y0 = float(cell["y0"]); y1 = float(cell["y1"])
#
#             xs0.append(x0); ys0.append(y0)
#             widths.append(abs(x1 - x0))
#             heights.append(abs(y1 - y0))
#
#             min_x0 = min(min_x0, x0)
#             max_x1 = max(max_x1, x1)
#             min_y0 = min(min_y0, y0)
#             max_y1 = max(max_y1, y1)
#
#             flat_cells.append(cell)
#
#     if not flat_cells or not math.isfinite(min_x0) or not math.isfinite(min_y0):
#         return []
#
#     dx = _mode_positive(widths)
#     dy = _mode_positive(heights)
#     if dx is None or dy is None:
#         raise ValueError("Konnte dx/dy (Zellgröße) nicht aus den Daten ableiten.")
#
#     # --- 2) Ziel-X0 und Ziel-Y0 Raster bauen ---
#     # x0 positions: min_x0, min_x0+dx, ... < max_x1
#     x_positions: List[float] = []
#     x = min_x0
#     # numerische Stabilität
#     while x < max_x1 - dx * 0.5:
#         x_positions.append(round(x, 9))
#         x += dx
#
#     # y0 positions: min_y0, min_y0+dy, ... < max_y1
#     y_positions: List[float] = []
#     if add_missing_y_rows:
#         y = min_y0
#         while y < max_y1 - dy * 0.5:
#             y_positions.append(round(y, 9))
#             y += dy
#     else:
#         # nur existierende Reihen verwenden (aus Input)
#         # (wir runden, um Float-Noise zu reduzieren)
#         y_positions = sorted({round(float(cell["y0"]), 9) for cell in flat_cells})
#
#     # --- 3) Mapping bauen: (y0, x0) -> cell ---
#     # Wichtig: input rows sind nach y0 sortiert, aber wir verlassen uns lieber nicht drauf.
#     cell_map: Dict[Tuple[float, float], Cell] = {}
#     for cell in flat_cells:
#         key = (round(float(cell["y0"]), 9), round(float(cell["x0"]), 9))
#         cell_map[key] = cell
#
#     # Defaults für leere Zellen
#     base_empty = {
#         "veg_height_max_a": 0.0,
#         "veg_height_max_b": 0.0,
#         "delta_z": 0.0,
#         "is_empty": True,
#     }
#     if empty_defaults:
#         base_empty.update(empty_defaults)
#
#     # --- 4) Rechteckige Matrix erzeugen und Löcher füllen ---
#     filled: Matrix = []
#     for y0 in y_positions:
#         row_out: List[Cell] = []
#         for x0 in x_positions:
#             key = (y0, x0)
#             existing = cell_map.get(key)
#             if existing is not None:
#                 # Markierung optional: falls du später schnell erkennen willst was echt ist
#                 if "is_empty" not in existing:
#                     existing = dict(existing)
#                     existing["is_empty"] = False
#                 row_out.append(existing)
#             else:
#                 # Placeholder erzeugen: Koordinaten rein, Werte default
#                 placeholder = dict(base_empty)
#                 placeholder["x0"] = x0
#                 placeholder["y0"] = y0
#                 placeholder["x1"] = round(x0 + dx, 9)
#                 placeholder["y1"] = round(y0 + dy, 9)
#                 row_out.append(placeholder)
#
#         filled.append(row_out)
#
#     return filled
#
#




def debug_print_matrix(matrix, name="Matrix"):
    """
    Gibt ein 2D-Array sauber formatiert aus, um die Korrektheit zu prüfen.
    Jede Zeile bekommt ihren Index, sowie die Spaltenanzahl.
    """
    logging.info(f"\n{name} — {len(matrix)} rows")
    logging.info("=" * 40)

    rows = len(matrix)
    logging.info(f"Rows: {rows}")
    for i, row in enumerate(matrix):
        logging.info(f"Row {i}: {len(row)} columns")

    logging.info("=" * 40 + "\n")


def to_2d(data):
    """
    Transforms 1D array into 2D array.
    """
    if not data:
        return []

    matrix = []
    current_y = data[0]["y0"]
    row = []

    for item in data:
        if item["y0"] != current_y:
            matrix.append(row)
            row = []
            current_y = item["y0"]
        row.append(item)

    matrix.append(row)
    return matrix



def validate_comparison_result(json_req):
    try:
        validate(instance=json_req, schema=comparison_schema)
    except ValidationError as e:
        logging.warning(f"The precompute job request is invalid, ValidationError error: {e}")
        return False  # TODO: Might be an exception here as well
    return True

def fetch_comparison_result(file_storage_details, publisher):
    temp_dir = tempfile.mkdtemp()
    try:
        return file_handler.fetch_file(file_storage_details, dest_dir=temp_dir)
    except HTTPError as e:
        logging.warning("Couldn't download file from: {}, error: {}".format(file_storage_details, e))
        publisher.publish_chunking_comparison_result(BaseMessage(type="chunking-comparison-result",
                                                           status="error",
                                                           job_id="",
                                                           payload=mk_error_msg(
                                                               error_msg="Couldn't download file from: {}, chunking-comparison job cancelled".format(
                                                                   file_storage_details))))
        return
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


def connect_rabbitmq():
    while True:
        try:
            return create_rabbit_con_and_return_channel()
        except Exception as e:
            logging.error("RabbitMQ connection failed, Retrying in 5s... Error: {}".format(e))
            time.sleep(5)


def mk_error_msg(error_msg: str):
    return {"msg": error_msg}



def main():
    channel = connect_rabbitmq()

    def callback(ch, method, properties, body):
        process_req(ch, method, properties, body)

    channel.basic_consume(queue=rabbitConfig.queue_chunking_comparison_job, on_message_callback=callback, auto_ack=True)

    logging.info("Waiting for messages")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        logging.warning("Worker interrupted by KeyboardInterrupt")
    except ReentrancyError as e:
        logging.error("Reentrancy error for start consuming on the channel: {}".format(e))
    except ChannelWrongStateError as e:
        logging.error("Channel error: {}".format(e))
    except StreamLostError as e:
        logging.error("Stream lost error: {}".format(e))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", default=False, action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    logging.basicConfig(level=(logging.DEBUG if args.verbose else logging.INFO))
    main()
