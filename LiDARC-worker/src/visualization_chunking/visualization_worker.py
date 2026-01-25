import argparse
import json
import logging
import shutil
import signal
import sys
import tempfile
import time
import math

from typing import List, Dict, Any, Optional
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
        cells_matrix = chunk_cells_average_veg_height(cells_matrix, chunking_size)
        debug_print_matrix(cells_matrix)

    message = build_response_message(comparison_id, cells_matrix, statistics, chunking_size, group_mapping, statistics_p)

    publisher.publish_chunking_comparison_result(BaseMessage(type="chunking_comparison_result",
                                                             status="success",
                                                             job_id="",
                                                             payload= message))

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


def chunk_cells_average_veg_height(
    cells_matrix: Matrix,
    chunk_size: int,
) -> Matrix:
    """
    Downsample a 2D grid of cells by grouping cells into blocks.

    - Block side length is sqrt(chunk_size)
    - chunk_size must be >= 16 and in 32 steps
    - For each block:
        - avg veg_height is computed over *existing* cells only (missing cells are ignored).
        - bounding box is computed from existing cells only.
    - Incomplete edge blocks are discarded.
    - Blocks with no existing cells are discarded.


    Each output cell represents one aggregated block.

    Input cell format example:
    {
        "x0": float,
        "x1": float,
        "y0": float,
        "y1": float,
        "veg_height_max_a": float,
        "veg_height_max_b": float,
        "delta_z": float
    }

    Output cell format:
    {
        "x0": first cell's x0 in the block,
        "x1": last cell's  x1 in the block,
        "y0": first cell's y0 in the block,
        "y1": last cell's  y1 in the block,
        "veg_height_max_a": mean of all a-values in the block,
        "veg_height_max_b": mean of all b-values in the block,
        "delta_z": sum(b-values) - sum(a-values)
                   (use mean_b - mean_a if needed instead)
    }


    """

    if not cells_matrix:
        return []

    if chunk_size < 1:
        raise ValueError("chunk_size must be >= 1.")


    rows = len(cells_matrix)
    if rows == 0:
        return []

    reduced: Matrix = []

    logging.info("Started actual chunking in worker...")
    # Step over block rows; discard incomplete blocks at the bottom edge
    for br in range(0, rows, chunk_size):
        if br + chunk_size > rows:
            break

        reduced_row: List[Cell] = []

        # We cannot assume rectangular rows; we will still discard incomplete right-edge blocks,
        # but we do so per block by checking whether *all* rows have enough columns for that block.
        # To find how far we can go to the right, we use the minimum row length within this block-row band.
        band_min_cols = min(len(cells_matrix[r]) for r in range(br, br + chunk_size))
        if band_min_cols == 0:
            # nothing usable in this band; move on
            continue

        for bc in range(0, band_min_cols, chunk_size):
            if bc + chunk_size > band_min_cols:
                break  # discard incomplete right-edge blocks

            sum_max_a = 0.0
            sum_max_b = 0.0
            sum_p_a = 0.0
            sum_p_b = 0.0
            count = 0
            has_percentile = False
            p_key=None

            min_x0: Optional[float] = None
            min_y0: Optional[float] = None
            max_x1: Optional[float] = None
            max_y1: Optional[float] = None

            # Aggregate over the block; ignore missing cells (ragged rows) by bounds checks
            for r in range(br, br + chunk_size):
                row = cells_matrix[r]
                for c in range(bc, bc + chunk_size):
                    if c >= len(row):
                        # Missing cell in this row at this column position -> ignore
                        continue
                    cell = row[c]
                    if not cell:
                        # If someone ever puts {} or None-like values here, ignore safely
                        continue

                    # veg_height is always filled per you
                    sum_max_a += float(cell["veg_height_max_a"])
                    sum_max_b += float(cell["veg_height_max_b"])

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

                    x0 = float(cell["x0"])
                    y0 = float(cell["y0"])
                    x1 = float(cell["x1"])
                    y1 = float(cell["y1"])

                    min_x0 = x0 if (min_x0 is None or x0 < min_x0) else min_x0
                    min_y0 = y0 if (min_y0 is None or y0 < min_y0) else min_y0
                    max_x1 = x1 if (max_x1 is None or x1 > max_x1) else max_x1
                    max_y1 = y1 if (max_y1 is None or y1 > max_y1) else max_y1

            # If the entire block is empty (only holes), discard it
            if count == 0:
                continue

            reduced_cell: Cell = {
                "x0": min_x0,
                "y0": min_y0,
                "x1": max_x1,
                "y1": max_y1,
                "veg_height_max_a": sum_max_a / count,
                "veg_height_max_b": sum_max_b / count,
                "delta_z": (sum_max_b - sum_max_a) / count,
            }

            if has_percentile:
                reduced_cell[f"{p_key}_a"] = sum_p_a / count
                reduced_cell[f"{p_key}_b"] = sum_p_b / count
                reduced_cell[f"{p_key}_diff"] = (sum_p_b - sum_p_a) / count

            reduced_row.append(reduced_cell)

        if reduced_row:
            reduced.append(reduced_row)

    return reduced


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
