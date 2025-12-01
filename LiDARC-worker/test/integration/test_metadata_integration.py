import json
import os

import pika
import pytest
from http import HTTPMethod
import src.metadata.metadata_worker as metadata_worker
import threading
from messaging.rabbit_config import get_rabbitmq_config

rabbitConfig = get_rabbitmq_config()
WORKER_EXCHANGE = rabbitConfig.exchange_worker_job
METADATA_JOB_QUEUE = rabbitConfig.queue_metadata_job
METADATA_RESULT_QUEUE = rabbitConfig.queue_metadata_result
METADATA_JOB_RK = rabbitConfig.routing_metadata_start

def publish_message(channel, exchange, routing_key, message_dict):
    channel.basic_publish(
        exchange=exchange,
        routing_key=routing_key,
        body=json.dumps(message_dict),
        properties=pika.BasicProperties(content_type="application/json"),
    )

def consume_single_message(channel, queue):
    method_frame, header_frame, body = None, None, ""

    def callback(ch, method, properties, body_msg):
        nonlocal method_frame, header_frame, body
        method_frame, header_frame, body = method, properties, body_msg
        ch.basic_ack(delivery_tag=method.delivery_tag)
        channel.stop_consuming()

    channel.basic_consume(queue=queue, on_message_callback=callback)
    channel.start_consuming()
    return body


@pytest.mark.e2e
def test_metadata_worker_integration_valid_request(minio_client, rabbitmq_ch, las_with_header_module_scope):
    client, upload_file = minio_client
    assert client.bucket_exists("basebucket")
    las_file_path = las_with_header_module_scope()
    upload_file(las_file_path, object_name="metadata_test.las")
    presigned_url = client.get_presigned_url(
        method=HTTPMethod.GET,
        bucket_name="basebucket",
        object_name="metadata_test.las"
    )

    def run_worker():
        metadata_worker.main()

    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()

    test_job = {
        "jobId": "12345",
        "url": presigned_url
    }
    publish_message(
        rabbitmq_ch,
        WORKER_EXCHANGE,
        METADATA_JOB_RK,
        test_job
    )

    body = consume_single_message(
        rabbitmq_ch,
        METADATA_RESULT_QUEUE
    )
    assert body is not None, "Metadata worker did not publish any result"

    response = json.loads(body)
    assert response["status"] == "success"
    assert response["job_id"] == "12345"


@pytest.mark.e2e
def test_metadata_worker_integration_invalid_url(minio_client, rabbitmq_ch, las_with_header_module_scope):
    assert minio_client.bucket_exists("basebucket")

    def run_worker():
        metadata_worker.main()

    worker_thread = threading.Thread(target=run_worker, daemon=True)
    worker_thread.start()

    test_job = {
        "jobId": "12345",
        "url": "http://localhost:12345/nonexistent_file.las"
    }
    publish_message(
        rabbitmq_ch,
        WORKER_EXCHANGE,
        METADATA_JOB_RK,
        message_dict=test_job
    )

    body = consume_single_message(
        rabbitmq_ch,
        METADATA_RESULT_QUEUE
    )
    assert body is not None, "Metadata worker did not publish any result"

    response = json.loads(body)
    assert response["status"] == "error"
    assert response["job_id"] == "12345"