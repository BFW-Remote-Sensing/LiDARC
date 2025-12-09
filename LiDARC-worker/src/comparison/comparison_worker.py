import logging
import shutil
import tempfile

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

def mk_error_msg(job_id: str, error_msg: str, comparison_id: str):
    return BaseMessage(
        type = "comparison",
        job_id = job_id,
        status = "error",
        payload={
            "msg": error_msg,
            "comparisonId": comparison_id
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



def compare_files(path_a: str, path_b: str) -> dict:
    #TODO handle errors
    df_a = pd.read_csv(path_a)
    df_b = pd.read_csv(path_b)
    logging.info("Loaded CSV A with %d rows", len(df_a))
    logging.info("Loaded CSV B with %d rows", len(df_b))

    # TODO should be in a defined format after preprocessing
    def normalize(df: pd.DataFrame, column: str = "veg_height_max") -> pd.DataFrame:
        if column not in df.columns:
            raise ValueError(f"Column '{column}' not found in dataframe")

        # Heuristic: if max height > 1000 → probably millimeters
        max_val = df[column].max()

        if max_val > 100:  # extremely unlikely for meters
            logging.info(f"Detected veg height in mm → converting to meters")
            df[column] = df[column] / 1000.0
        else:
            logging.info("Detected veg height already in meters")
        return df

    df_a = normalize(df_a, "veg_height_max")
    df_b = normalize(df_b, "veg_height_max")

    merged = df_a.merge(
        df_b,
        on=["x0", "x1", "y0", "y1"],
        suffixes=("_a", "_b")
    )

    # TODO double check if just remove cells where values are -inf, inf is OK?
    valid_mask = (
            np.isfinite(merged["veg_height_max_a"]) &
            np.isfinite(merged["veg_height_max_b"])
    )
    merged = merged[valid_mask]

    merged["delta_z"] = merged["veg_height_max_b"] - merged["veg_height_max_a"]

    logging.info("Merged into %d matched grid cells", len(merged))


    logging.info("Calculating Statistics:")

    # basic metrics of veg_height b
    stats_b = {
        "mean_veg_height": merged["veg_height_max_b"].mean(),
        "median_veg_height": merged["veg_height_max_b"].median(),
        "std_veg_height":merged["veg_height_max_b"].std(),
        "min_veg_height": merged["veg_height_max_b"].min(),
        "max_veg_height": merged["veg_height_max_b"].max(),
        "percentiles": {
            "p10": float(np.percentile(merged["veg_height_max_b"], 10)),
            "p25": float(np.percentile(merged["veg_height_max_b"], 25)),
            "p50": float(np.percentile(merged["veg_height_max_b"], 50)),
            "p75": float(np.percentile(merged["veg_height_max_b"], 75)),
            "p90": float(np.percentile(merged["veg_height_max_b"], 90)),
        }
    }

    # basic metrics of veg_height b
    stats_a = {
        "mean_veg_height": merged["veg_height_max_a"].mean(),
        "median_veg_height": merged["veg_height_max_a"].median(),
        "std_veg_height": merged["veg_height_max_a"].std(),
        "min_veg_height": merged["veg_height_max_a"].min(),
        "max_veg_height": merged["veg_height_max_a"].max(),
        "percentiles": {
            "p10": float(np.percentile(merged["veg_height_max_a"], 10)),
            "p25": float(np.percentile(merged["veg_height_max_a"], 25)),
            "p50": float(np.percentile(merged["veg_height_max_a"], 50)),
            "p75": float(np.percentile(merged["veg_height_max_a"], 75)),
            "p90": float(np.percentile(merged["veg_height_max_a"], 90)),
        }
    }

    # calculate basic statistics of the difference
    diffs = merged["delta_z"]
    neg = diffs[diffs <= 0]
    pos = diffs[diffs >= 0]

    stats_diff = {
        "mean": diffs.mean(),
        "median": diffs.median(),
        "std": diffs.std(),
        "most_negative": float(neg.min()) if not neg.empty else None,
        "least_negative": float(neg.max()) if not neg.empty else None,
        "smallest_positive": float(pos.min()) if not pos.empty else None,
        "largest_positive": float(pos.max()) if not pos.empty else None,
        "percentiles": {
            "p10": float(np.percentile(diffs, 10)),
            "p25": float(np.percentile(diffs, 25)),
            "p50": float(np.percentile(diffs, 50)),
            "p75": float(np.percentile(diffs, 75)),
            "p90": float(np.percentile(diffs, 90)),
        },
        #TODO also calculate the slope for visualization?
        "pearson_corr": float(merged["veg_height_max_a"].corr(merged["veg_height_max_b"]))
    }


    # TODO consider if needed (probably also histogram bins?)
    #logging.info("Simple Categorization:")
    # categorization
    #categories = pd.cut(
    #    merged["delta_z"].abs(),
    #    bins=[0, 2, 4, 5, np.inf],
    #    labels=["almost equal", "slightly different", "different", "highly different"]
    #)
    #category_counts = categories.value_counts()
    #logging.info("Simple Categorization:")
    #logging.info(f"Category counts:\n{category_counts}")

    cells = merged.apply(
        lambda row: {
            "x0": row["x0"],
            "x1": row["x1"],
            "y0": row["y0"],
            "y1": row["y1"],
            "veg_height_max_a": row["veg_height_max_a"],
            "veg_height_max_b": row["veg_height_max_b"],
            "delta_z": row["delta_z"],
        },
        axis=1
    ).tolist()

    result = {
        "cells": cells,
        "statistics": {
            "file_a": stats_a,
            "file_b": stats_b,
            "difference": stats_diff
        }
    }
    return result



def process_req(ch, method, props, body):
    start_time = time.time()
    job_id = ""
    comparison_id = ""
    temp_dir = tempfile.mkdtemp()
    try:
        req = json.loads(body)
        if not req["jobId"]:
            logging.warning("The comparison job is cancelled because there is no job id")
            publish_response(ch, mk_error_msg(job_id="", error_msg="Comparison job is cancelled because job has no job id", comparison_id=""))

        if not req["comparisonId"]:
            logging.warning("The comparison job is cancelled because there is no comparison id")
            publish_response(ch, mk_error_msg(job_id="", error_msg="Comparison job is cancelled because job has no comparison id", comparison_id=""))

        job_id = req["jobId"]
        comparison_id = req["comparisonId"]
        if not validate_request(req):
            logging.warning("The comparison job is cancelled because of a Validation Error")
            publish_response(ch, mk_error_msg(job_id, "Comparison job is cancelled because job request is invalid", comparison_id=comparison_id))
            return

        downloaded_files = []
        for f in req["files"]:
            try:
                local_path = file_handler.fetch_file(
                    {
                        "bucket": f["bucket"],
                        "objectKey": f["objectKey"]
                    },
                    dest_dir=temp_dir
                )
                downloaded_files.append(local_path)

            except HTTPError as e:
                logging.warning(f"Couldn't download file {f}: {e}")
                publish_response(ch, mk_error_msg(job_id, f"Couldn't download file from MinIO", comparison_id=comparison_id))
                return


        comparison_result = compare_files(downloaded_files[0], downloaded_files[1])

        destination_file = f"comparison-job-{job_id}.json"

        try:
            result = file_handler.upload_json(destination_file, comparison_result)
            payload = {
                "result": result,
                "comparisonId": comparison_id
            }
            publish_response(ch, mk_success_msg(job_id, payload))
        except Exception as e:
            logging.error(f"Failed to upload JSON comparison file to MinIO: {e}")
            publish_response(ch, mk_error_msg(job_id, "Failed to store comparison result in MinIO", comparison_id))
            return

    except Exception as e:
        logging.error(f"Failed to process message: {e}")
        publish_response(ch, mk_error_msg(job_id, "An unexpected error occured, comparison job cancelled", comparison_id))
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)
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
