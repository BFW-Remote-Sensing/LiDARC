import json
import logging
import os
import laspy
from pyproj import CRS
import time
import re
from urllib.parse import urlparse

import pika
import requests
from requests.adapters import HTTPAdapter, Retry
from requests.exceptions import HTTPError

logging.basicConfig(level=logging.INFO, format='%(asctime)s | %(levelname)s | %(message)s')


def download_file(url: str, dest_dir: str = ".", chunk_size: int = 10* 1024 ) -> str:
    os.makedirs(dest_dir, exist_ok=True)
    parsed = urlparse(url)
    local_filename = os.path.join(dest_dir, os.path.basename(parsed.path))

    session = requests.Session()
    retries = Retry(
        total=5,
        backoff_factor=1,
        status_forcelist=[500, 502, 503, 504],
        allowed_methods=["GET"],
    )
    session.mount("https://", HTTPAdapter(max_retries=retries))
    session.mount("http://", HTTPAdapter(max_retries=retries))
    try:
        with requests.get(url, stream=True) as r:
            r.raise_for_status()
            with open(local_filename, 'wb') as f:
                for chunk in r.iter_content(chunk_size=chunk_size):
                    f.write(chunk)
        return local_filename
    except Exception as e:
        if os.path.exists(local_filename):
            os.remove(local_filename)
        raise RuntimeError(f"Download failed for {url}: {e}") from e


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
        logging.info(f"Processing file from URL: {las_file_url}")

        local_file = download_file(las_file_url)

        metadata = extract_metadata(local_file)
        logging.info(f"Metadata extracted: {json.dumps(metadata)}")

        ch.basic_publish(
            exchange=worker_results_exchange,
            routing_key=worker_key_metadata,
            body = json.dumps(metadata),
            properties = pika.BasicProperties(content_type="application/json")
        )
        logging.info(f"Sent metadata to exchange: {worker_results_exchange}")


        os.remove(local_file)
        logging.info(f"Removed local file: {local_file}")
    except Exception as e:
        logging.error(f"Failed to process message: {e}")
    finally:
        processing_time = int((time.time() - start_time) * 1000)
        logging.info(f"Worker took {processing_time} ms to process the message")


def main():
    queue_name = "metadata_trigger"
    worker_results_exchange = "worker-results"
    logging.info(f"Connecting to RabbitMQ...")
    while True:
        try:
            connection = pika.BlockingConnection(
                pika.ConnectionParameters(
                    host="rabbitmq",
                    port=5672,
                    virtual_host="/",
                    credentials=pika.PlainCredentials(username='admin', password='admin'),
                )
            )
            channel = connection.channel()
            channel.queue_declare(queue=queue_name, durable=True)
            channel.exchange_declare(exchange=worker_results_exchange, exchange_type="direct", durable=True)
            logging.info(f"Connected to RabbitMQ Listening on queue '{queue_name}'")
            break
        except Exception as e:
            logging.warning(f"RabbitMQ Connection Error: {e}. Retrying in 5 seconds...")
            time.sleep(5)

    channel.basic_consume(queue=queue_name, on_message_callback=process_req, auto_ack=True)
    channel.start_consuming()


if __name__ == '__main__':
    main()