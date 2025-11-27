import argparse
import json
import os
import shutil
import tempfile

import pika
import logging
import time
import laspy
import sys
import signal
import pandas as pd
import numpy as np
from jsonschema.exceptions import ValidationError
from jsonschema.validators import validate
from pika.exceptions import ChannelWrongStateError, ReentrancyError, StreamLostError
from tdigest import TDigest
from schemas.precompute import schema as precompute_schema
import util.file_handler as file_handler
from requests import HTTPError

def handle_sigterm(signum, frame):
    logging.info("SIGTERM received")
    sys.exit(0)

signal.signal(signal.SIGTERM, handle_sigterm)

def connect_rabbitmq():
    while True:
        try:
            user = os.environ.get("RABBITMQ_USER", "admin")
            password = os.environ.get("RABBITMQ_PASSWORD", "admin")
            host = os.environ.get("RABBITMQ_HOST", "rabbitmq")
            port = int(os.environ.get("RABBITMQ_PORT", "5672"))
            vhost = os.environ.get("RABBITMQ_VHOST", "/worker")

            credentials = pika.PlainCredentials(username=user, password=password)
            connection = pika.BlockingConnection(pika.ConnectionParameters(host=host, port=port, virtual_host=vhost, credentials=credentials))
            return connection
        except Exception as e:
            logging.error("RabbitMQ connection failed, Retrying in 5s... Error: {}".format(e))
            time.sleep(5)


def calculate_grid(grid: dict):
    x_min = grid["xMin"]
    x_max = grid["xMax"]
    y_min = grid["yMin"]
    y_max = grid["yMax"]

    grid_width = grid["x"]
    grid_height = grid["y"]

    grid_cells_x = int(np.ceil((x_max - x_min) / grid_width))
    grid_cells_y = int(np.ceil((y_max - y_min) / grid_height))

    grid_shape = (grid_cells_y, grid_cells_x)

    count = np.zeros(grid_shape, dtype=np.uint32)
    z_min = np.full(grid_shape, np.inf,  dtype=np.float32)
    z_max = np.full(grid_shape, -np.inf, dtype=np.float32)
    veg_height_min = np.full(grid_shape, np.inf, dtype=np.float32)
    veg_height_max = np.full(grid_shape, -np.inf, dtype=np.float32)
    veg_height_digest = np.empty(grid_shape, dtype=object)
    for r in range(grid_shape[0]):
        for c in range(grid_shape[1]):
            veg_height_digest[r,c] = TDigest()
    return {
        "grid_shape": grid_shape,
        "grid_width": grid_width,
        "grid_height": grid_height,
        "z_max": z_max,
        "z_min": z_min,
        "veg_height_min": veg_height_min,
        "veg_height_max": veg_height_max,
        "count": count,
        "x_min": x_min,
        "y_min": y_min,
        "veg_height_digest": veg_height_digest
    }

def validate_request(json_req):
    try:
        validate(instance=json_req, schema=precompute_schema)
    except ValidationError as e:
        logging.warning(f"The precompute job request is invalid, ValidationError error: {e}")
        return False #TODO: Might be an exception here as well
    return True

def process_points(points, precomp_grid):
    logging.debug("Processing points: {}".format(points))
    x = points.x
    y = points.y
    z = np.array(points.z)
    veg_height = points[precomp_grid["veg_height_key"]]

    ix = ((x - precomp_grid["x_min"]) / precomp_grid["grid_width"]).astype(np.int32)
    iy = ((y - precomp_grid["y_min"]) / precomp_grid["grid_height"]).astype(np.int32)

    valid = (ix >= 0) & (iy >= 0) & (ix < precomp_grid["grid_shape"][1]) & (iy < precomp_grid["grid_shape"][0])
    ix, iy, z, veg_height = ix[valid], iy[valid], z[valid], veg_height[valid]

    np.add.at(precomp_grid["count"], (iy, ix), 1)
    np.minimum.at(precomp_grid["z_min"], (iy, ix), z)
    np.maximum.at(precomp_grid["z_max"], (iy, ix), z)
    np.minimum.at(precomp_grid["veg_height_min"], (iy, ix), veg_height)
    np.maximum.at(precomp_grid["veg_height_max"], (iy, ix), veg_height)

    n_rows, n_cols = precomp_grid["count"].shape

    for r, c, vh in zip(iy, ix, veg_height):
        if 0 <= r < n_rows and 0 <= c < n_cols:
            precomp_grid["veg_height_digest"][r,c].update(float(vh))

def mk_error_msg(job_id: str, error_msg: str):
    return {"jobId": job_id, "status": "error", "msg": error_msg}

def publish_response(ch, response_dict):
    ch.basic_publish(
        exchange=os.environ.get("EXCHANGE_NAME", "worker.job"),
        routing_key="job.preprocessor.create",
        body=json.dumps(response_dict),
        properties = pika.BasicProperties("application/json")
    )
def mk_summary(grid_shape, df: pd.DataFrame):
    return {
        "nCells": int(grid_shape[0] * grid_shape[1]),
        "maxZ": float(df["z_max"].max()),
        "minZ": float(df["z_min"].min()),
        "maxVegHeight": float(df["veg_height_max"].max()),
        "minVegHeight": float(df["veg_height_min"].min())
    }

def create_result_df(precomp_grid):
    rows, cols = np.nonzero(precomp_grid["count"])

    grid_width = precomp_grid["grid_width"]
    grid_height = precomp_grid["grid_height"]
    x_min = precomp_grid["x_min"]
    y_min = precomp_grid["y_min"]

    p90 = []
    p95 = []
    digests = precomp_grid["veg_height_digest"]

    for r, c in zip(rows, cols):
        d = digests[r, c]
        if d.n > 0:
            p90.append(d.percentile(90))
            p95.append(d.percentile(95))
        else:
            p90.append(np.nan)
            p95.append(np.nan)

    return pd.DataFrame({
        "x0": x_min + cols * grid_width,
        "x1": x_min + (cols + 1) * grid_width,
        "y0": y_min + rows * grid_height,
        "y1": y_min + (rows + 1) * grid_height,
        "count": precomp_grid["count"][rows, cols],
        "z_max": precomp_grid["z_max"][rows, cols],
        "z_min": precomp_grid["z_min"][rows, cols],
        "veg_height_max": precomp_grid["veg_height_max"][rows, cols],
        "veg_height_min": precomp_grid["veg_height_min"][rows, cols],
        "veg_p90": p90,
        "veg_p95": p95,
    })


#TODO: LOOK at how to maybe process points better or use less resources? Any suggestions are warmly welcome
def process_req(ch, method, properties, body):
    start_time = time.time()
    request = json.loads(body)
    if "jobId" not in request:
        logging.warning("The precompute job is cancelled because there is no job id")
        publish_response(ch, mk_error_msg(job_id="-42", error_msg="Precompute job is cancelled because job has no job id"))

    job_id = request["jobId"]
    if not validate_request(request):
        logging.warning("The precompute job is cancelled because of an Validation Error ")
        publish_response(ch, mk_error_msg(job_id, "Precompute job is cancelled because job request is invalid"))
        return

    #Process request
    las_file_url = request["url"]
    grid = request["grid"]

    temp_dir = tempfile.mkdtemp()
    try:
        downloaded_file_fn = file_handler.download_file(las_file_url, dest_dir=temp_dir)

        precomp_grid = calculate_grid(grid)
        precomp_grid["veg_height_key"] = "gps_time"

        with laspy.open(downloaded_file_fn) as f:
            if "ndsm" in  f.header.point_format.extra_dimension_names:
                logging.info("File to process is using ndsm for vegetational height of trees")
                precomp_grid["veg_height_key"] = "ndsm"
            logging.info("Processing point cloud from file: {}".format(las_file_url))
            for points in f.chunk_iterator(500_000):
                process_points(points, precomp_grid)
                del points

        df = create_result_df(precomp_grid)
        upload_result = file_handler.upload_file_by_type(f"pre-process-job-{job_id}-output.csv", df)

        processing_time = int((time.time() - start_time) * 1000)
        logging.info("Worker took {} ms to process the request".format(processing_time))

        response = {
            "jobId": job_id,
            "status": "success",
            "result": upload_result,
            "summary": mk_summary(precomp_grid["grid_shape"], df)
        }
        publish_response(ch, response)
    #TODO: Add exceptions correctly!
    except HTTPError as e:
        logging.warning("Couldn't download file from: {}, error: {}".format(las_file_url, e))
        publish_response(ch, mk_error_msg(job_id, "Couldn't download file from: {}, precompute job cancelled".format(las_file_url)))
        return
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)

def main():
    channel = connect_rabbitmq().channel()
    queue_name = os.environ.get("QUEUE_NAME", "preprocessing.job")
    channel.queue_declare(queue=queue_name, durable=True)
    def callback(ch, method, properties, body):
        process_req(ch, method, properties, body)

    channel.basic_consume(queue=queue_name, on_message_callback=callback, auto_ack=True)

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