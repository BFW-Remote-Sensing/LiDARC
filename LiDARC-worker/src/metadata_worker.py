import logging
import time
import pika

logging.basicConfig(level=logging.INFO, format='%(asctime)s | %(levelname)s | %(message)s')

def process_message(ch, method, properties, body):
    logging.info(f"Received message: {body.decode()}")

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

    channel.basic_consume(queue=queue_name, on_message_callback=process_message, auto_ack=True)
    channel.start_consuming()


if __name__ == '__main__':
    main()