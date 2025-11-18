import json
import logging
import os
import laspy
import argparse
import signal
import sys
from pyproj import CRS
import time
import re

import pika
import requests
from requests.adapters import HTTPAdapter, Retry
from requests.exceptions import HTTPError
import util.file_handler as file_handler

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


def parse_coordinate_system(header) -> str:
    # try to parse crs from header
    try:
        crs = header.parse_crs()
        if crs:
            auth_code = crs.to_authority()
            if auth_code:
                authority, code = auth_code
                return f"{authority.upper()}:{code}"
    except:
        pass

    # if no crs found in header --> fallback to system_identifier
    system_id = getattr(header, "system_identifier", "").strip()
    if system_id:
        m = re.match(r"^(\d{3,7})", system_id)
        if m:
            code = int(m.group(1))
            for auth in ["EPSG", "ESRI", "IGNF"]:
                try:
                    CRS.from_authority(auth, code)
                    return f"{auth}:{code}"
                except:
                    continue
    # if nothing found --> UNKNOWN:UNKNOWN
    return f"UNKNOWN:UNKNOWN"


def extract_metadata(file_path: str) -> dict:
    try:
        filename = os.path.basename(file_path)
        match = re.search(r"\d{4}", filename)
        capture_year = int(match.group(0)) if match else None
        size_bytes = os.path.getsize(file_path)

        with laspy.open(file_path) as las:
            header = las.header

            metadata = {
                "filename": filename,
                "capture_year": capture_year,
                "size_bytes": size_bytes,
                "min_x": header.x_min,
                "min_y": header.y_min,
                "min_z": header.z_min,
                "max_x": header.x_max,
                "max_y": header.y_max,
                "max_z": header.z_max,
                "system_identifier": header.system_identifier,
                "las_version": f"{header.version[0]}.{header.version[1]}",
                "capture_software": header.generating_software,
                "point_count": header.point_count,
                "file_creation_date": str(header.creation_date),
                "coordinate_system": parse_coordinate_system(header)
            }
        return metadata

    except Exception as e:
        logging.error(f"Metadata extraction failed for {file_path}: {e}")
        return {}


def process_req(ch, method, properties, body):
    start_time = time.time()
    worker_results_exchange = "worker-results"
    worker_key_metadata = "worker.result.metadata"

    try:
        req = json.loads(body)
        las_file_url = req["url"]
        logging.info(f"Processing file from URL: {las_file_url}.")

        local_file = ""
        try:
            local_file = file_handler.download_file(las_file_url)
        except HTTPError as e:
            logging.warning("Couldn't download file from: {}, error: {}".format(las_file_url, e))
            return
        if local_file == "":
            logging.warning("File not downloaded, stopping processing the request!")
            return

        metadata = extract_metadata(local_file)
        logging.debug(f"Metadata extracted: {json.dumps(metadata)}")

        ch.basic_publish(
            exchange=worker_results_exchange,
            routing_key=worker_key_metadata,
            body = json.dumps(metadata),
            properties = pika.BasicProperties(content_type="application/json")
        )
        logging.debug(f"Sent metadata to exchange: {worker_results_exchange}")


        os.remove(local_file)
        logging.debug(f"Removed local file: {local_file}")
    except Exception as e:
        logging.error(f"Failed to process message: {e}")
    finally:
        processing_time = int((time.time() - start_time) * 1000)
        logging.info(f"Worker took {processing_time} ms to process the message.")


def main():
    queue_name = "metadata_trigger"

    connection = connect_rabbitmq()
    channel = connection.channel()

    channel.queue_declare(queue=queue_name, durable=True)
    channel.basic_consume(queue=queue_name, on_message_callback=process_req, auto_ack=True)
    logging.info(f"Connected to RabbitMQ Listening on queue '{queue_name}'")
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