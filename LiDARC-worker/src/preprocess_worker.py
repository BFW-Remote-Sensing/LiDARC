import argparse
import json
from urllib.parse import urlparse

import pdal
import pika
import logging
import time
import requests
import os
from requests import HTTPError
from requests.adapters import HTTPAdapter, Retry

def connect_rabbitmq():
    while True:
        try:
            credentials = pika.PlainCredentials(username='admin', password='admin') #TODO: set to environment vars
            connection = pika.BlockingConnection(pika.ConnectionParameters(host='rabbitmq', port=5672, credentials=credentials))
            return connection
        except Exception as e:
            logging.error("RabbitMQ connection failed, Retrying in 5s... Error: {}".format(e))
            time.sleep(5)

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
    logging.info("Worker took {} ms to process the request".format(processing_time))

def main():
    connection = connect_rabbitmq()
    channel = connection.channel()
    queue_name = "preprocess"
    def callback(ch, method, properties, body):
        process_req(ch, method, properties, body)

    channel.basic_consume(queue=queue_name, on_message_callback=callback, auto_ack=True)

    logging.info("Waiting for messages")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        logging.warning("Worker interrupted")
    except Exception as e:
        logging.error("Worker error: {}".format(e))

    data = "graz2021_block6_060_065_elv.las"
    print("Hello World!")
    pipeline = pdal.Reader.las(filename=data).pipeline()
    print(pipeline.execute())
    arr = pipeline.arrays[0]
    print(arr)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", default=False, action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    logging.basicConfig(level=(logging.DEBUG if args.verbose else logging.INFO))
    main()