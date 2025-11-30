import json
import uuid
import pika

from messaging.rabbit_config import rabbitConfig

# ===========================
# Configuration for Tests
# ===========================
# 1

RESULT_EXCHANGE = rabbitConfig.exchange_worker_results
TEST_RESULT_QUEUE = "test.result"
TEST_RESULT_RK = "test.result"

METADATA_RESULT_QUEUE = rabbitConfig.queue_metadata_result
COMPARISON_RESULT_QUEUE = rabbitConfig.queue_comparison_result
PREPROCESSING_RESULT_QUEUE = rabbitConfig.queue_preprocessing_result

METADATA_RESULT_RK = rabbitConfig.routing_metadata_result
COMPARISON_RESULT_RK = rabbitConfig.routing_comparison_result
PREPROCESSING_RESULT_RK = rabbitConfig.routing_preprocessing_result

# ===========================
# Tests
# ===========================

def test_can_connect_to_rabbit(rabbitmq_ch):
    """
    Minimal-test: Connection exists and is open.
    """
    channel = rabbitmq_ch
    assert channel.is_open


def test_can_publish_and_consume_on_an_default_queue(rabbitmq_ch):
    """
    General test for channel and publish and consume on default queue
    """
    # temporäre Queue anlegen (server-generated Name)
    result = rabbitmq_ch.queue_declare(queue="", exclusive=True, auto_delete=False)
    queue_name = result.method.queue

    payload = {"foo": "bar", "id": str(uuid.uuid4())}
    body = json.dumps(payload).encode("utf-8")

    rabbitmq_ch.basic_publish(
        exchange="",                 # default exchange
        routing_key=queue_name,
        body=body,
        properties=pika.BasicProperties(
            content_type="application/json",
            delivery_mode=1,
        ),
    )

    method, properties, received_body = rabbitmq_ch.basic_get(
        queue=queue_name,
        auto_ack=True,
    )

    assert method is not None, "No message read from queue"
    assert received_body is not None

    received_payload = json.loads(received_body.decode("utf-8"))
    assert received_payload == payload


def test_result_exchange_with_declared_test_queue_roundtrip(rabbitmq_ch):
    """
    Tests routing of result exchange and test result queue with specified test routing key
    """
    # Declaration of result exchange and separate queues (idempotent)
    # declare usually with definitions.json
    rabbitmq_ch.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbitmq_ch.queue_declare(queue=TEST_RESULT_QUEUE, durable=True, auto_delete=False)
    rabbitmq_ch.queue_bind(
        queue=TEST_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=TEST_RESULT_RK,
    )
    payload = {"job": "preprocess", "value": 42}
    body = json.dumps(payload).encode("utf-8")

    rabbitmq_ch.basic_publish(
        exchange="",
        routing_key=TEST_RESULT_QUEUE,
        body=body,
        properties=pika.BasicProperties(
            content_type="application/json",
        ),
    )

    method, properties, received_body = rabbitmq_ch.basic_get(
        queue=TEST_RESULT_QUEUE,
        auto_ack=True,
    )

    assert method is not None
    received_payload = json.loads(received_body.decode("utf-8"))
    print(received_payload)
    assert received_payload["job"] == "preprocess"
    assert received_payload["value"] == 42


def test_result_publisher_on_result_exchange_routing(result_publisher, rabbitmq_ch):
    """
    Tests implemented result publisher and routing over the result exchange
   """

    #Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbitmq_ch.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbitmq_ch.queue_declare(queue=TEST_RESULT_QUEUE, durable=True, auto_delete=False)
    rabbitmq_ch.queue_bind(
        queue=TEST_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=TEST_RESULT_RK,
    )

    payload = {"job": "test_queue", "value": 42}

    result_publisher._publish(TEST_RESULT_RK, payload, "test")

    method, properties, received_body = rabbitmq_ch.basic_get(
        queue=TEST_RESULT_QUEUE,
        auto_ack=True,
    )


    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "No message on Results-Exchange"
    assert received_payload["type"] == "test"
    assert received_payload["payload"]["value"] == 42
    assert received_payload["payload"]["job"] == "test_queue"

def test_result_publisher_on_metadata_routing(result_publisher, rabbitmq_ch):
    """
        Tests implemented result publisher and routing over the result exchange
        for metadata publish
       """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json at rabbit container startup
    rabbitmq_ch.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbitmq_ch.queue_declare(queue=METADATA_RESULT_QUEUE, durable=True, auto_delete=False)
    rabbitmq_ch.queue_bind(
        queue=METADATA_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=METADATA_RESULT_RK,
    )

    payload = {"job": "metadata", "value": 120}

    result_publisher.publish_metadata_result(payload)

    method, properties, received_body = rabbitmq_ch.basic_get(
        queue=METADATA_RESULT_QUEUE,
        auto_ack=True,
    )

    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "No message on Results-Exchange"
    assert received_payload["type"] == METADATA_RESULT_RK
    assert received_payload["payload"]["value"] == 120
    assert received_payload["payload"]["job"] == "metadata"


def test_result_publisher_on_comparison_routing(result_publisher, rabbitmq_ch):
    """
        Tests implemented result publisher and routing over the result exchange
        for comparison publish
       """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbitmq_ch.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )

    rabbitmq_ch.queue_declare(queue=COMPARISON_RESULT_QUEUE, durable=True, auto_delete=False)

    rabbitmq_ch.queue_bind(
        queue=COMPARISON_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=COMPARISON_RESULT_RK,
    )

    payload = {"job": "comparison", "value": 120}

    result_publisher.publish_comparison_result(payload)

    method, properties, received_body = rabbitmq_ch.basic_get(
        queue=COMPARISON_RESULT_QUEUE,
        auto_ack=True,
    )

    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "No message on Results-Exchange"
    assert received_payload["type"] == COMPARISON_RESULT_RK
    assert received_payload["payload"]["value"] == 120
    assert received_payload["payload"]["job"] == "comparison"


def test_result_publisher_on_preprocessing_routing(result_publisher, rabbitmq_ch):
    """
        Tests implemented result publisher and routing over the result exchange
        for preprocessing publish
       """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbitmq_ch.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )

    rabbitmq_ch.queue_declare(queue=PREPROCESSING_RESULT_QUEUE, durable=True, auto_delete=False)

    rabbitmq_ch.queue_bind(
        queue=PREPROCESSING_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=PREPROCESSING_RESULT_RK,
    )

    payload = {"job": "preprocessing", "value": 120}

    result_publisher.publish_preprocessing_result(payload)

    method, properties, received_body = rabbitmq_ch.basic_get(
        queue=PREPROCESSING_RESULT_QUEUE,
        auto_ack=True,
    )

    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "No message on Results-Exchange"
    assert received_payload["type"] == PREPROCESSING_RESULT_RK
    assert received_payload["payload"]["value"] == 120
    assert received_payload["payload"]["job"] == "preprocessing"
