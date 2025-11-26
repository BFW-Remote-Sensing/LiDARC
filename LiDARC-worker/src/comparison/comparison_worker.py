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


TRIGGER_QUEUE_NAME = "worker_comparison_job"
WORKER_RESULTS_EXCHANGE = "worker-results"
WORKER_RESULTS_KEY_COMPARISON = "worker.comparison.result"

def handle_sigterm(signum, frame):
    logging.info("SIGTERM received")
    sys.exit(0)

signal.signal(signal.SIGTERM, handle_sigterm)

def connect_rabbitmq():
    logging.info(f"Connecting to RabbitMQ...")
    while True:
        try:
            user = os.environ.get("RABBITMQ_USER", "admin")
            password = os.environ.get("RABBITMQ_PSWD", "admin")
            host = os.environ.get("RABBITMQ_HOST", "rabbitmq")
            port = os.environ.get("RABBITMQ_PORT", "5672")
            vhost = os.environ.get("RABBITMQ_VHOST", "/worker")

            credentials = pika.PlainCredentials(username=user, password=password)
            connection = pika.BlockingConnection(
                pika.ConnectionParameters(
                    host=host,
                    port=port,
                    virtual_host=vhost,
                    credentials=credentials
                )
            )
            return connection
        except Exception as e:
            logging.error("RabbitMQ connection failed, Retrying in 5s... Error: {}".format(e))
            time.sleep(5)

def mk_error_msg(job_id: str, error_msg: str):
    return {"jobId": job_id, "status": "error", "msg": error_msg}

def publish_response(ch, response_dict):
    ch.basic_publish(
        exchange=WORKER_RESULTS_EXCHANGE,
        routing_key=WORKER_RESULTS_KEY_COMPARISON,
        body=json.dumps(response_dict),
        properties=pika.BasicProperties(content_type="application/json")
    )
    logging.debug(f"Sent response to exchange: {WORKER_RESULTS_EXCHANGE}")

def process_req(ch, method, props, body):
    # TODO check for message structure and content, and download corresponding files (just mocked for now)
    logging.info("Received MSG! Downloading files...")
    csv1 = "/data/pre-process-job-123-output.csv"
    csv2 = "/data/pre-process-job-456-output.csv"
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



def main():
    connection = connect_rabbitmq()
    channel = connection.channel()

    channel.basic_consume(queue=TRIGGER_QUEUE_NAME, on_message_callback=process_req, auto_ack=True)
    logging.info(f"Connected to RabbitMQ Listening on queue '{TRIGGER_QUEUE_NAME}'")
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
