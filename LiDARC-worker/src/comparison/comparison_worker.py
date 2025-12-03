import logging
import pika
import time
import json
import argparse
import signal
import sys
import os
import pandas as pd
import numpy as np
from schemas.comparison import schema as comparison_schema
from jsonschema.exceptions import ValidationError
from jsonschema.validators import validate
from messaging.message_model import BaseMessage
from messaging.rabbit_connect import create_rabbit_con_and_return_channel
from messaging.result_publisher import ResultPublisher
from messaging.rabbit_config import get_rabbitmq_config
import requests
from requests.adapters import HTTPAdapter, Retry
from requests.exceptions import HTTPError
import util.file_handler as file_handler

rabbitConfig = get_rabbitmq_config()

def handle_sigterm(signum, frame):
    logging.info("SIGTERM received")
    sys.exit(0)

signal.signal(signal.SIGTERM, handle_sigterm)

def connect_rabbitmq():
    logging.info(f"Connecting to RabbitMQ...")
    while True:
        try:
            return create_rabbit_con_and_return_channel()
        except Exception as e:
            logging.error("RabbitMQ connection failed, Retrying in 5s... Error: {}".format(e))
            time.sleep(5)

def mk_error_msg(job_id: str, error_msg: str):
    return BaseMessage(
        type = "comparison",
        job_id = job_id,
        status = "error",
        payload={
            "msg": error_msg
        }
    )

def mk_success_msg(job_id: str, comparison_result: dict):
    return BaseMessage(
        type = "comparison",
        job_id = job_id,
        status = "success",
        payload={"result":comparison_result}
    )

def publish_response(ch, msg: BaseMessage):
    publisher = ResultPublisher(ch)
    publisher.publish_comparison_result(msg)
    logging.debug(f"Sent response to exchange: {rabbitConfig.exchange_worker_results}")

def validate_request(json_req):
    try:
        validate(instance=json_req, schema=comparison_schema)
        return True
    except ValidationError as e:
        logging.warning(f"The comparison job request is invalid")
        return False

def compare_files() -> dict:
    # TODO check for message structure and content, and download corresponding files (just mocked for now)
    logging.info("Received MSG! Downloading files...")
    csv1 = "/data/pre-process-job-1010-output.csv"
    csv2 = "/data/pre-process-job-1015-output.csv"
    df_a = pd.read_csv(csv1)
    df_b = pd.read_csv(csv2)
    logging.info("Loaded CSV A with %d rows", len(df_a))
    logging.info("Loaded CSV B with %d rows", len(df_b))

    merged = df_a.merge(
        df_b,
        on=["x0", "x1", "y0", "y1"],
        suffixes=("_a", "_b")
    )

    merged["delta_z"] = df_b["veg_height_max"] - df_a["veg_height_max"]

    logging.info("Merged into %d matched grid cells", len(merged))
    for _, row in merged.iterrows():
        cell = f"[{row.x0},{row.x1}] x [{row.y0},{row.y1}]"
        veg_a = row.veg_height_max_a
        veg_b = row.veg_height_max_b
        delta_z = veg_b - veg_a
        logging.info(f"Cell {cell}: veg_height_max (A={veg_a}, B={veg_b}), delta_z={delta_z}")

    logging.info("Calculating Statistics:")
    # calculate basic statistics
    mean_diff = merged["delta_z"].mean()
    median_diff = merged["delta_z"].median()
    std_diff = merged["delta_z"].std()
    min_diff = merged["delta_z"].min()
    max_diff = merged["delta_z"].max()
    percentiles = np.percentile(merged["delta_z"], [10, 25, 50, 75, 90])
    logging.info(f"Mean Difference: {mean_diff}")
    logging.info(f"Median Difference: {median_diff}")
    logging.info(f"Std Difference: {std_diff}")
    logging.info(f"Min Difference: {min_diff}")
    logging.info(f"Max Difference: {max_diff}")
    logging.info(f"Percentiles [10,25,50,75,90]: {percentiles}")

    logging.info("Simple Categorization::")
    # example categorization
    categories = pd.cut(
        merged["delta_z"].abs(),
        bins=[0, 2, 4, 5, np.inf],
        labels=["almost equal", "slightly different", "different", "highly different"]
    )
    category_counts = categories.value_counts()
    logging.info(f"Category counts:\n{category_counts}")

    logging.info("Calculating Correlation:")
    pearson_corr = merged["veg_height_max_a"].corr(merged["veg_height_max_b"])
    logging.info(f"Pearson correlation between A and B: {pearson_corr}")

    return {}


def process_req(ch, method, props, body):
    start_time = time.time()
    job_id = ""
    try:
        req = json.loads(body)
        if not req["jobId"]:
            logging.warning("The comparison job is cancelled because there is no job id")
            publish_response(ch, mk_error_msg(job_id="", error_msg="Comparison job is cancelled because job has no job id"))

        job_id = req["jobId"]
        if not validate_request(req):
            logging.warning("The comparison job is cancelled because of a Validation Error")
            publish_response(ch, mk_error_msg(job_id, "Comparison job is cancelled because job request is invalid"))
            return

        # files_local = []
        # for file_info in req["files"]:
        #     file_url = file_info["url"]
        #     file_name = file_info["originalFilename"]
        #
        #     try:
        #         local_file = file_handler.download_file(file_url)
        #     except HTTPError as e:
        #         logging.warning("Couldn't download file from: {}, error: {}".format(file_url, e))
        #         publish_response(ch, mk_error_msg(job_id, "Couldn't download file from: {}, comparison job cancelled".format(file_url)))
        #         return
        #     if local_file == "":
        #         logging.warning("File not downloaded, stopping processing the request!")
        #         publish_response(ch, mk_error_msg(job_id, "Couldn't download file from: {}, comparison job cancelled".format(file_url)))
        #         return
        #     files_local.append({
        #         "originalFilename": file_name,
        #         "path": local_file
        #     })
        #
        #
        # for f in files_local:
        #     os.remove(f["path"])


    except Exception as e:
        logging.error(f"Failed to process message: {e}")
        publish_response(ch, mk_error_msg(job_id, "An unexpected error occured, comparison job cancelled"))
    finally:
        processing_time = int((time.time() - start_time) * 1000)
        logging.info(f"Worker took {processing_time} ms to process the message.")



def main():
    channel = connect_rabbitmq()

    channel.basic_consume(queue=rabbitConfig.queue_comparison_job, on_message_callback=process_req, auto_ack=True)
    logging.info(f"Connected to RabbitMQ Listening on queue '{rabbitConfig.queue_comparison_job}'")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        logging.warning("Worker interrupted")
    except Exception as e:
        logging.error("Worker error: {}".format(e))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", default=False, action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    logging.basicConfig(level=(logging.DEBUG if args.verbose else logging.INFO))
    main()
