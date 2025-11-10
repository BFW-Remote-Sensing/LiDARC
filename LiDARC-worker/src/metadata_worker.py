import json
import logging
import os
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

def process_req(ch, method, properties, body):
    start_time = time.time()
    request = json.loads(body)

    #Process request
    las_file_url = request["url"]
    try:
        download_file(las_file_url)
    except HTTPError as e:
        logging.warning("Couldn't download file from: {}, error: {}".format(las_file_url, e))
    processing_time = int((time.time() - start_time) * 1000)
    logging.info("Worker took {} ms to process the file".format(processing_time))

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