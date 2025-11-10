import json
import logging
import os
import subprocess
import time
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

def extract_metadata(file_path: str) -> dict:
    try:
        result = subprocess.run(
            ["pdal", "info", "--metadata", file_path],
            capture_output=True,
            text=True
        )
        if result.returncode != 0:
            raise RuntimeError(f"pdal error: {result.stderr.strip()}")

        pdal_out = json.loads(result.stdout)
        meta = pdal_out.get("metadata", {})

        reader_key = next((k for k in meta.keys() if k.startswith("readers.")), None)
        header = meta[reader_key] if reader_key else meta

        minimal_meta = {
            "filename": os.path.basename(file_path),
            "size_bytes": os.path.getsize(file_path),

            "creation_year": header.get("creation_year"),

            "min_x": header.get("minx"),
            "min_y": header.get("miny"),
            "min_z": header.get("minz"),
            "min_gpstime": header.get("min_gpstime"),

            "max_x": header.get("maxx"),
            "max_y": header.get("maxy"),
            "max_z": header.get("maxz"),
            "max_gpstime": header.get("max_gpstime"),

            "coordinate_system": header.get("system_id"),
            "las_version": f"{header.get('major_version')}.{header.get('minor_version')}" if header.get(
                "major_version") else None,
            "capture_software": header.get("software_id"),
        }
        return minimal_meta
    except Exception as e:
        logging.error(f"Metadata extraction failed for {file_path}: {e}")
        return {}


def process_req(ch, method, properties, body):
    start_time = time.time()
    try:
        req = json.loads(body)
        las_file_url = req["url"]
        logging.info(f"Processing file from URL: {las_file_url}")

        local_file = download_file(las_file_url)

        metadata = extract_metadata(local_file)
        logging.info(f"Metadata extracted: {json.dumps(metadata)}")

        os.remove(local_file)
        logging.info(f"Removed local file: {local_file}")
    except Exception as e:
        logging.error(f"Failed to process message: {e}")
    finally:
        processing_time = int((time.time() - start_time) * 1000)
        logging.info(f"Worker took {processing_time} ms to process the message")


def main():
    queue_name = "metadata_trigger"
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
            logging.info(f"Connected to RabbitMQ Listening on queue '{queue_name}'")
            break
        except Exception as e:
            logging.warning(f"RabbitMQ Connection Error: {e}. Retrying in 5 seconds...")
            time.sleep(5)

    channel.basic_consume(queue=queue_name, on_message_callback=process_req, auto_ack=True)
    channel.start_consuming()


if __name__ == '__main__':
    main()