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
from schemas.metadata import schema as metadata_schema
from jsonschema.exceptions import ValidationError
from jsonschema.validators import validate

from src.messaging.message_model import BaseMessage
from src.messaging.rabbit_connect import create_connection, create_channel
from src.messaging.result_publisher import ResultPublisher
from src.messaging.topology import topology

publisher = ResultPublisher()

def handle_sigterm(signum, frame):
    logging.info("SIGTERM received")
    sys.exit(0)

signal.signal(signal.SIGTERM, handle_sigterm)

def connect_rabbitmq():
    logging.info(f"Connecting to RabbitMQ...")
    while True:
        try:
            user = os.environ.get("RABBITMQ_USER", "admin")
            password = os.environ.get("RABBITMQ_PASSWORD", "admin")
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
    return BaseMessage(
        type = "metadata",
        version = "1",
        job_id = job_id,
        status = "error",
        payload={
            "msg": error_msg
        }
    )

def mk_success_msg(job_id: str, metadata: dict):
    return BaseMessage(
        type = "metadata",
        version = "1",
        job_id = job_id,
        status = "success",
        payload=metadata
    )

def publish_response(msg: BaseMessage):
    publisher.publish_metadata_result(msg)
    logging.debug(f"Sent response to exchange: {topology.exchange_worker_results}")

def validate_request(json_req):
    try:
        validate(instance=json_req, schema=metadata_schema)
        return True
    except ValidationError as e:
        logging.warning(f"The metadata job request is invalid")
        return False


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
            for auth in ["EPSG", "ESRI"]:
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
        return {}


def process_req(ch, method, properties, body):
    start_time = time.time()
    job_id = ""
    try:
        req = json.loads(body)
        if not req["jobId"]:
            logging.warning("The metadata job is cancelled because there is no job id")
            publish_response(mk_error_msg(job_id="", error_msg="Metadata job is cancelled because job has no job id"))

        job_id = req["jobId"]

        if not validate_request(req):
            logging.warning("The metadata job is cancelled because of a Validation Error")
            publish_response(mk_error_msg(job_id, "Metadata job is cancelled because job request is invalid"))
            return

        las_file_url = req["url"]
        logging.info(f"Processing file from URL: {las_file_url}.")

        local_file = ""
        try:
            local_file = file_handler.download_file(las_file_url)
        except HTTPError as e:
            logging.warning("Couldn't download file from: {}, error: {}".format(las_file_url, e))
            publish_response(mk_error_msg(job_id, "Couldn't download file from: {}, metadata job cancelled".format(las_file_url)))
            return
        if local_file == "":
            logging.warning("File not downloaded, stopping processing the request!")
            publish_response(mk_error_msg(job_id, "Couldn't download file from: {}, metadata job cancelled".format(las_file_url)))
            return

        metadata = extract_metadata(local_file)

        if metadata == {}:
            logging.error(f"Metadata extraction failed for {las_file_url}.")
            publish_response(mk_error_msg(job_id, "Couldn't extract metadata from file from: {}, metadata job cancelled".format(las_file_url)))
            return

        publish_response(mk_success_msg(job_id, metadata))
        os.remove(local_file)
    except Exception as e:
        logging.error(f"Failed to process message: {e}")
        publish_response(mk_error_msg(job_id, "An unexpected error occured, metadata job cancelled"))
    finally:
        processing_time = int((time.time() - start_time) * 1000)
        logging.info(f"Worker took {processing_time} ms to process the message.")


def main():
    connection = create_connection()
    channel = create_channel(connection)

    channel.basic_consume(queue=topology.queue_metadata_job, on_message_callback=process_req, auto_ack=True)
    logging.info(f"Connected to RabbitMQ Listening on queue '{topology.queue_metadata_job}'")
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