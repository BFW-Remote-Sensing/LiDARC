import argparse
import json

import pika
import logging
import time
import laspy
import pandas as pd
import numpy as np
import util.file_handler as file_handler

from requests import HTTPError


def connect_rabbitmq():
    while True:
        try:
            credentials = pika.PlainCredentials(username='admin', password='admin') #TODO: set to environment vars
            connection = pika.BlockingConnection(pika.ConnectionParameters(host='rabbitmq', port=5672, credentials=credentials))
            return connection
        except Exception as e:
            logging.error("RabbitMQ connection failed, Retrying in 5s... Error: {}".format(e))
            time.sleep(5)


def calculate_grid(grid: dict):
    x_min = grid["x_min"]
    x_max = grid["x_max"]
    y_min = grid["y_min"]
    y_max = grid["y_max"]

    grid_width = grid["x"]
    grid_height = grid["y"]

    grid_cells_x = int(np.ceil((x_max - x_min) / grid_width))
    grid_cells_y = int(np.ceil((y_max - y_min) / grid_height))

    grid_shape = (grid_cells_y, grid_cells_x)

    count = np.zeros(grid_shape, dtype=np.uint32)
    z_sum = np.zeros(grid_shape, dtype=np.float32)
    z_min = np.full(grid_shape, np.inf,  dtype=np.float32)
    z_max = np.full(grid_shape, -np.inf, dtype=np.float32)

    return {
        "grid_shape": grid_shape,
        "grid_width": grid_width,
        "grid_height": grid_height,
        "z_max": z_max,
        "count": count,
        "x_min": x_min,
        "y_min": y_min,
    }
def process_points(points, precomp_grid):
    logging.debug("Processing points: {}".format(points))
    x = points.x
    y = points.y
    z = points.z

    ix = ((x - precomp_grid["x_min"]) / precomp_grid["grid_width"]).astype(np.int32)
    iy = ((y - precomp_grid["y_min"]) / precomp_grid["grid_height"]).astype(np.int32)

    np.add.at(precomp_grid["count"], (iy, ix), 1)

def write_result_to_minio(df):
    pass

def process_req(ch, method, properties, body):
    start_time = time.time()
    request = json.loads(body)

    #Process request
    las_file_url = request["url"]
    # { "url": "", ... }
    grid = request["grid"]
    #TODO: What if grid already processed and defined in BE

    downloaded_file_fn = ""
    try:
        downloaded_file_fn = file_handler.download_file(las_file_url)
    except HTTPError as e:
        logging.warning("Couldn't download file from: {}, error: {}".format(las_file_url, e))
        return

    precomp_grid = calculate_grid(grid)

    with laspy.open(downloaded_file_fn) as f:
        logging.info("Processing point cloud from file: {}".format(las_file_url))
        for points in f.chunk_iterator(1_000_000):
            process_points(points, precomp_grid)

    rows, cols = np.nonzero(precomp_grid["count"])

    grid_width = precomp_grid["grid_width"]
    grid_height = precomp_grid["grid_height"]
    x_min = precomp_grid["x_min"]
    y_min = precomp_grid["y_min"]

    df = pd.DataFrame({
        "x0": x_min + cols * grid_width,
        "x1": x_min + (cols + 1) * grid_width,
        "y0": y_min + rows * grid_height,
        "y1": y_min + (rows + 1) * grid_height,
        "count": precomp_grid["count"][rows, cols]
    })
    job_id = request["job_id"]
    df.to_csv(f"pre-process-job-{job_id}-output.csv")
    file_handler.upload_file_by_type(f"pre-process-job-{job_id}-output.csv", df)

    write_result_to_minio(df)

    processing_time = int((time.time() - start_time) * 1000)

    #TODO: Respond to RabbitMQ

    logging.info("Worker took {} ms to process the request".format(processing_time))



def main():
    # BE -> newJobPreProc -> RabbitMQ -QUEUE> preprocess_worker.py
    # BE -> WORKER_EXCHANGE -> preprocess_trigger -> preprocess_worker.py
    #
    # preprocess_worker.py -> WORKER_EXCHANGE , routing_key="worker.preprocess" -> BE
    #
    # { "fileName": "graz2021_block6_060_065_elv.laz", "grid": [{ "gridId": 0, "gridFromId": 0, "gridToId": 100, "maxHeight": 10, "maxX": 100, "maxY": 20}]}
    # 400m x 400m = 160 000m^2 -> (worst Case grid = 1m^2) = 160 000 grid cells
    #
    # (worst case, region mit 7000 files) -> 1 120 000 000
    connection = connect_rabbitmq()
    channel = connection.channel()
    queue_name = "worker.preprocess"
    def callback(ch, method, properties, body):
        process_req(ch, method, properties, body)

    channel.basic_consume(queue=queue_name, on_message_callback=callback, auto_ack=True)

    logging.info("Waiting for messages")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        logging.warning("Worker interrupted")
    except Exception as e:
        logging.error("Worker error: {}".format(e))

    print("Hello World!")

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", default=False, action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    logging.basicConfig(level=(logging.DEBUG if args.verbose else logging.INFO))
    main()