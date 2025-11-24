import logging
import pika
import time
import json
import argparse
import signal
import sys
import os


TRIGGER_QUEUE_NAME = "worker_comparison_job"
WORKER_RESULTS_EXCHANGE = "worker-results"
WORKER_RESULTS_KEY_METADATA = "worker.comparison.result"

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
        routing_key=WORKER_RESULTS_KEY_METADATA,
        body=json.dumps(response_dict),
        properties=pika.BasicProperties(content_type="application/json")
    )
    logging.debug(f"Sent response to exchange: {WORKER_RESULTS_EXCHANGE}")

def process_req(ch, method, props, body):
    logging.info("Received MSG!")


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
