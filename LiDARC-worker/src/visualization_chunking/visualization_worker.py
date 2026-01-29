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
    statistics_p = None
    if "statistics_p" in comparison_result:
        statistics_p = comparison_result["statistics_p"]



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

    message = build_response_message(comparison_id, cells_matrix, statistics, chunking_size, group_mapping, statistics_p)

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

def build_response_message(comparison_id, chunked_matrix, statistics, chunking_size, group_mapping, statistics_p = None):
    response = {
        "comparisonId": comparison_id,
        "chunkingSize": chunking_size,
        "chunked_cells": chunked_matrix,
        "statistics": statistics,
        "group_mapping": group_mapping
    }
    if statistics_p is not None:
        response["statistics_p"] = statistics_p
    return response





Cell = Dict[str, Any]
Matrix = List[List[Cell]]


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
            sum_p_a = 0.0
            sum_p_b = 0.0
            count = 0
            has_percentile = False
            p_key=None
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
                    # veg_height is always filled per you


                    if not has_percentile:
                        for k in cell.keys():
                            if k.startswith("veg_height_p") and k.endswith("_a"):
                                p_key = k[:-2]
                                has_percentile = True
                                break
                    if has_percentile:
                        sum_p_a += float(cell[f"{p_key}_a"])
                        sum_p_b += float(cell[f"{p_key}_b"])

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

            if has_percentile:
                reduced_cell[f"{p_key}_a"] = sum_p_a / count
                reduced_cell[f"{p_key}_b"] = sum_p_b / count
                reduced_cell[f"{p_key}_diff"] = (sum_p_b - sum_p_a) / count

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
