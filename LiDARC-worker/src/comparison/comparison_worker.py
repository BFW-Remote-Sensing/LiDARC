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
        payload=comparison_result
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

def round_floats(obj, ndigits=2):
    if isinstance(obj, float):
        return round(obj, ndigits)
    if isinstance(obj, (np.floating,)):
        return round(float(obj), ndigits)
    if isinstance(obj, dict):
        return {k: round_floats(v, ndigits) for k, v in obj.items()}
    if isinstance(obj, list):
        return [round_floats(v, ndigits) for v in obj]
    return obj

def calculate_difference_statistics(
        diffs: pd.Series,
        veg_height_a: pd.Series,
        veg_height_b: pd.Series,
        group_a: str,
        group_b: str,
        name: str,
        bins: int = 10,
) -> dict:

    neg = diffs[diffs <= 0]
    pos = diffs[diffs >= 0]

    # calculate histogram of difference
    min_diff = diffs.min()
    max_diff = diffs.max()
    if min_diff == max_diff:
        min_diff -= 0.5
        max_diff += 0.5
    q_low, q_high = np.percentile(diffs, [1, 99])
    bin_edges = np.linspace(q_low, q_high, bins + 1)
    counts, _ = np.histogram(diffs, bins=bin_edges)
    histogram = {
        "bin_edges": bin_edges.tolist(),
        "counts": counts.tolist()
    }

    # calculate linear regression of B vs A
    x = veg_height_a
    y = veg_height_b
    n = len(x)
    sum_x = np.sum(x)
    sum_y = np.sum(y)
    sum_xy = (x * y).sum()
    sum_x2 = (x * x).sum()
    den = n * sum_x2 - sum_x ** 2
    if den == 0:
        slope = None
        intercept = None
    else:
        slope = (n * sum_xy - sum_x * sum_y) / den
        intercept = (sum_y - slope * sum_x) / n

    return {
        "name": name,
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
        "histogram": histogram,
        "correlation": {
            "pearson_correlation": float(veg_height_a.corr(veg_height_b)),
            "regression_line": {
                "slope": None if slope is None else float(slope),
                "intercept": None if intercept is None else float(intercept),
                "x_min": float(x.min()),
                "x_max": float(x.max()),
            }
        }
    }


def calculate_file_statistics(data: pd.Series, name: str) -> dict:
    return {
        "name": name,
        "mean_veg_height": data.mean(),
        "median_veg_height": data.median(),
        "std_veg_height": data.std(),
        "min_veg_height": data.min(),
        "max_veg_height": data.max(),
        "percentiles": {
            "p10": float(np.percentile(data, 10)),
            "p25": float(np.percentile(data, 25)),
            "p50": float(np.percentile(data, 50)),
            "p75": float(np.percentile(data, 75)),
            "p90": float(np.percentile(data, 90))
        }
    }

def calculate_comparison_statistics(merged: pd.DataFrame, group_a: str, group_b: str) -> dict:
    logging.info("Calculating comparison statistics...")

    percentile_columns = [col for col in merged.columns if col.endswith("_a") and col.startswith("veg_height_p")]
    percentile_a = percentile_columns[0] if percentile_columns else None
    percentile_b = percentile_a.replace("_a", "_b") if percentile_a else None
    percentile_diff = percentile_a.replace("_a", "_diff") if percentile_a else None

    for col in ["veg_height_max_a", "veg_height_max_b"]:
        merged[col] = (
            merged[col]
            .replace([np.inf, -np.inf], np.nan)
            .fillna(0.0)
        )

    for col in percentile_columns:
        col_b = col.replace("_a", "_b")
        diff_col = col.replace("_a", "_diff")
        if col not in merged.columns:
            merged[col] = 0.0
        if col_b not in merged.columns:
            merged[col_b] = 0.0
        merged[col] = merged[col].replace([np.inf, -np.inf], np.nan).fillna(0.0)
        merged[col_b] = merged[col_b].replace([np.inf, -np.inf], np.nan).fillna(0.0)
        merged[diff_col] = merged[col_b] - merged[col]

    merged["delta_z"] = merged["veg_height_max_b"] - merged["veg_height_max_a"]
    logging.debug("Calculating statistics on %d grid cells", len(merged))

    # basic metrics of veg_height b
    stats_b = calculate_file_statistics(merged["veg_height_max_b"], "stats of veg_height_max_b")
    stats_b["mean_points_per_grid_cell"] = float(merged["count_b"].mean())

    # basic metrics of veg_height a
    stats_a = calculate_file_statistics(merged["veg_height_max_a"], "stats of veg_height_max_a")
    stats_a["mean_points_per_grid_cell"] = float(merged["count_a"].mean())


    # calculate basic statistics of the difference
    diffs = merged["delta_z"]
    stats_diff = calculate_difference_statistics(
        diffs=diffs,
        veg_height_a=merged["veg_height_max_a"],
        veg_height_b=merged["veg_height_max_b"],
        group_a=group_a,
        group_b=group_b,
        name=f"stats of difference of max height {group_b} - {group_a}",
        bins=10
    )

    stats_p = None
    if percentile_a is not None:
        stats_p_b = calculate_file_statistics(
            merged[percentile_b],
            "stats of veg_height_p_b"
        )
        stats_p_b["mean_points_per_grid_cell"] = float(merged["count_b"].mean())

        stats_p_a = calculate_file_statistics(
            merged[percentile_a],
            "stats of veg_height_p_a"
        )
        stats_p_a["mean_points_per_grid_cell"] = float(merged["count_a"].mean())
        # difference stats for percentile heights
        stats_p_diff = calculate_difference_statistics(
            diffs=merged[percentile_diff],
            veg_height_a=merged[percentile_a],
            veg_height_b=merged[percentile_b],
            group_a=group_a,
            group_b=group_b,
            name=f"stats of difference of percentile height {group_b} - {group_a}",
            bins=10
        )

        stats_p = {
            "file_a": stats_p_a,
            "file_b": stats_p_b,
            "difference": stats_p_diff
        }


    cells = merged.apply(
        lambda row: {
            "x0": row["x0"],
            "x1": row["x1"],
            "y0": row["y0"],
            "y1": row["y1"],
            "veg_height_max_a": row["veg_height_max_a"],
            "veg_height_max_b": row["veg_height_max_b"],
            "delta_z": row["delta_z"],
            "count_a": row["count_a"],
            "count_b": row["count_b"],
            "out_a": row["out_a"],
            "out_b": row["out_b"],
            "out_c7_a": row["out_c7_a"],
            "out_c7_b": row["out_c7_b"],
            **{col: row[col] for col in percentile_columns},
            **{col.replace("_a", "_b"): row[col.replace("_a", "_b")] for col in percentile_columns},
            ** {col.replace("_a", "_diff"): row[col.replace("_a", "_diff")] for col in percentile_columns}

        },
        axis=1
    ).tolist()

    result = {
        "group_mapping": {
            "a": group_a,
            "b": group_b
        },
        "cells": cells,
        "statistics": {
            "file_a": stats_a,
            "file_b": stats_b,
            "difference": stats_diff
        }
    }

    if stats_p is not None:
        result["statistics_p"] = stats_p

    return round_floats(result)

def is_legit_value(v):
    if v is None:
        return False
    if isinstance(v, (float, np.floating)):
        if np.isnan(v) or np.isinf(v):
            return False
        if v == 0.0:
            return False
    return True

def set_if_missing(container: dict, key: str, new_value):
    old_val = container.get(key)
    if not is_legit_value(old_val):
        container[key] = new_value


def build_merged_dataframe(
    files: list,
    temp_dir: str,
    group_a: str,
    group_b: str,
) -> pd.DataFrame:
    cells = {}

    def normalize_df(df: pd.DataFrame) -> pd.DataFrame:
        max_val = df["veg_height_max"].max()
        if max_val > 100:
            logging.debug("Detected veg height in mm → converting to meters")
            df["veg_height_max"] /= 1000.0
        for col in df.columns:
            if col.startswith("veg_height_p"):
                df[col] /= 1000.0 if df[col].max() > 100 else 1.0
        return df


    for f in files:
        logging.info("Adding file to dataframe...")
        group = f["groupName"]

        if group == group_a:
            slot = "a"
        elif group == group_b:
            slot = "b"
        else:
            # should never happen if validated earlier
            logging.warning("Skipping unknown groupName '%s'", group)
            continue

        local_path = file_handler.fetch_file(
            {"bucket": f["bucket"], "objectKey": f["objectKey"]},
            dest_dir=temp_dir
        )

        try:
            df = pd.read_csv(local_path)
            df = normalize_df(df)

            percentile_cols = [col for col in df.columns if col.startswith("veg_height_p")]

            for idx, row in df.iterrows():
                key = (row["x0"], row["x1"], row["y0"], row["y1"])

                if key not in cells:
                    cells[key] = {"a": None, "b": None}

                set_if_missing(cells[key], slot, row["veg_height_max"])
                if slot == "a":
                    set_if_missing(cells[key], "count_a", row.get("count", 0))
                    set_if_missing(cells[key], "out_a", getattr(row, "veg_height_outlier_count", 0))
                    set_if_missing(cells[key], "out_c7_a", getattr(row, "veg_height_outlier_class7_count", 0))
                if slot == "b":
                    set_if_missing(cells[key], "count_b", row.get("count", 0))
                    set_if_missing(cells[key], "out_b", getattr(row, "veg_height_outlier_count", 0))
                    set_if_missing(cells[key], "out_c7_b", getattr(row, "veg_height_outlier_class7_count", 0))

                for col in percentile_cols:
                    if "percentiles" not in cells[key]:
                        cells[key]["percentiles"] = {}
                    if col not in cells[key]["percentiles"]:
                        cells[key]["percentiles"][col] = {"a": None, "b": None}
                    set_if_missing(cells[key]["percentiles"][col],slot,row[col])

        finally:
            os.remove(local_path)

    rows = []
    for (x0, x1, y0, y1), values in cells.items():
        if values["a"] is not None and values["b"] is not None:
            row_data = {
                "x0": x0,
                "x1": x1,
                "y0": y0,
                "y1": y1,
                "veg_height_max_a": values["a"],
                "veg_height_max_b": values["b"],
                "count_a": values.get("count_a", 0),
                "count_b": values.get("count_b", 0),
                "out_a": values.get("out_a", 0),
                "out_b": values.get("out_b", 0),
                "out_c7_a": values.get("out_c7_a", 0),
                "out_c7_b": values.get("out_c7_b", 0),
            }
            for col, col_vals in values.get("percentiles", {}).items():
                row_data[f"{col}_a"] = col_vals["a"]
                row_data[f"{col}_b"] = col_vals["b"]
            rows.append(row_data)

    merged = pd.DataFrame(rows)
    logging.debug("Merged into %d matched grid cells", len(merged))
    return merged


def process_req(ch, method, props, body):
    start_time = time.time()
    job_id = ""
    comparison_id = ""
    temp_dir = tempfile.mkdtemp()
    try:
        logging.info("Starting comparison job %s", comparison_id)
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


        #downloaded_files = []
        #for f in req["files"]:
        #    try:
        #        local_path = file_handler.fetch_file(
        #            {
        #                "bucket": f["bucket"],
        #                "objectKey": f["objectKey"]
        #            },
        #            dest_dir=temp_dir
        #        )
        #        downloaded_files.append(local_path)
        #
        #    except HTTPError as e:
        #       logging.warning(f"Couldn't download file {f}: {e}")
        #        publish_response(ch, mk_error_msg(job_id, f"Couldn't download file from MinIO", comparison_id=comparison_id))
        #        return


        group_names = sorted({f["groupName"] for f in req["files"]})
        group_a, group_b = group_names[0], group_names[1]
        if len(group_names) != 2:
            publish_response(ch,mk_error_msg(job_id,"Exactly two distinct groupName values are required",comparison_id))
            return


        merged_df = build_merged_dataframe(req["files"], temp_dir, group_a, group_b)
        comparison_result = calculate_comparison_statistics(merged_df, group_a, group_b)


        destination_file = f"comparison-job-{job_id}.json"

        try:
            result = file_handler.upload_json(destination_file, comparison_result)
            payload = {
                "comparisonId": int(comparison_id),
                "result": result
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
