import json
import uuid
import pika

from messaging.topology import topology

# ===========================
# Configuration for Tests
# ===========================


RESULT_EXCHANGE = topology.exchange_worker_results
TEST_RESULT_QUEUE = "test.result"
TEST_RESULT_RK = "test.result"

METADATA_QUEUE = topology.queue_metadata_result
COMPARISON_QUEUE = topology.queue_comparison_result
PREPROCESSING_QUEUE = topology.queue_preprocessing_result

METADATA_RK = topology.routing_metadata_result
COMPARISON_RK = topology.routing_comparison_result
PREPROCESSING_RK = topology.routing_preprocessing_result

# ===========================
# Tests
# ===========================

def test_can_connect_to_rabbit(rabbit_connection):
    """
    Minimal-test: Connection exists and is open.
    """
    assert rabbit_connection.is_open


def test_can_publish_and_consume_on_an_default_queue(rabbit_channel):
    """
    General test for channel and publish and consume on default queue
    """
    # temporäre Queue anlegen (server-generated Name)
    result = rabbit_channel.queue_declare(queue="", exclusive=True, auto_delete=True)
    queue_name = result.method.queue

    payload = {"foo": "bar", "id": str(uuid.uuid4())}
    body = json.dumps(payload).encode("utf-8")

    rabbit_channel.basic_publish(
        exchange="",                 # default exchange
        routing_key=queue_name,
        body=body,
        properties=pika.BasicProperties(
            content_type="application/json",
            delivery_mode=1,
        ),
    )

    method, properties, received_body = rabbit_channel.basic_get(
        queue=queue_name,
        auto_ack=True,
    )

    assert method is not None, "No message read from queue"
    assert received_body is not None

    received_payload = json.loads(received_body.decode("utf-8"))
    assert received_payload == payload


def test_result_exchange_with_declared_test_queue_roundtrip(rabbit_channel):
    """
    Tests routing of result exchange and test result queue with specified test routing key
    """
    # Declaration of result exchange and separate queues (idempotent)
    # declare usually with definitions.json
    rabbit_channel.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbit_channel.queue_declare(queue=TEST_RESULT_QUEUE, durable=False, auto_delete=True)
    rabbit_channel.queue_bind(
        queue=TEST_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=TEST_RESULT_RK,
    )
    payload = {"job": "preprocess", "value": 42}
    body = json.dumps(payload).encode("utf-8")

    rabbit_channel.basic_publish(
        exchange="",
        routing_key=TEST_RESULT_QUEUE,
        body=body,
        properties=pika.BasicProperties(
            content_type="application/json",
        ),
    )

    method, properties, received_body = rabbit_channel.basic_get(
        queue=TEST_RESULT_QUEUE,
        auto_ack=True,
    )

    assert method is not None
    received_payload = json.loads(received_body.decode("utf-8"))
    print(received_payload)
    assert received_payload["job"] == "preprocess"
    assert received_payload["value"] == 42


def test_result_publisher_on_result_exchange_routing(result_publisher, rabbit_channel):
    """
    Tests implemented result publisher and routing over the result exchange
   """

    #Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbit_channel.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbit_channel.queue_declare(queue=TEST_RESULT_QUEUE, durable=False, auto_delete=True)
    rabbit_channel.queue_bind(
        queue=TEST_RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=TEST_RESULT_RK,
    )

    payload = {"job": "test_queue", "value": 42}

    result_publisher._publish(TEST_RESULT_RK, payload, "test")

    method, properties, received_body = rabbit_channel.basic_get(
        queue=TEST_RESULT_QUEUE,
        auto_ack=True,
    )


    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "Keine Nachricht über den Results-Exchange erhalten"
    assert received_payload["type"] == "test"
    assert received_payload["payload"]["value"] == 42
    assert received_payload["payload"]["job"] == "test_queue"

def test_result_publisher_on_metadata_routing(result_publisher, rabbit_channel):
    """
        Tests implemented result publisher and routing over the result exchange
        for metadata publish
       """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json at rabbit container startup
    rabbit_channel.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbit_channel.queue_declare(queue=METADATA_QUEUE, durable=False, auto_delete=True)
    rabbit_channel.queue_bind(
        queue=METADATA_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=METADATA_RK,
    )

    payload = {"job": "metadata", "value": 120}

    result_publisher.publish_metadata_result(payload)

    method, properties, received_body = rabbit_channel.basic_get(
        queue=METADATA_QUEUE,
        auto_ack=True,
    )

    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "Keine Nachricht über den Results-Exchange erhalten"
    assert received_payload["type"] == METADATA_RK
    assert received_payload["payload"]["value"] == 120
    assert received_payload["payload"]["job"] == "metadata"


def test_result_publisher_on_comparison_routing(result_publisher, rabbit_channel):
    """
        Tests implemented result publisher and routing over the result exchange
        for comparison publish
       """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbit_channel.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )

    rabbit_channel.queue_declare(queue=COMPARISON_QUEUE, durable=False, auto_delete=True)

    rabbit_channel.queue_bind(
        queue=COMPARISON_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=COMPARISON_RK,
    )

    payload = {"job": "comparison", "value": 120}

    result_publisher.publish_comparison_result(payload)

    method, properties, received_body = rabbit_channel.basic_get(
        queue=COMPARISON_QUEUE,
        auto_ack=True,
    )

    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "Keine Nachricht über den Results-Exchange erhalten"
    assert received_payload["type"] == COMPARISON_RK
    assert received_payload["payload"]["value"] == 120
    assert received_payload["payload"]["job"] == "comparison"


def test_result_publisher_on_preprocessing_routing(result_publisher, rabbit_channel):
    """
        Tests implemented result publisher and routing over the result exchange
        for preprocessing publish
       """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbit_channel.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )

    rabbit_channel.queue_declare(queue=PREPROCESSING_QUEUE, durable=False, auto_delete=True)

    rabbit_channel.queue_bind(
        queue=PREPROCESSING_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=PREPROCESSING_RK,
    )

    payload = {"job": "preprocessing", "value": 120}

    result_publisher.publish_preprocessing_result(payload)

    method, properties, received_body = rabbit_channel.basic_get(
        queue=PREPROCESSING_QUEUE,
        auto_ack=True,
    )

    assert method is not None

    received_payload = json.loads(received_body.decode("utf-8"))

    assert received_payload is not None, "Keine Nachricht über den Results-Exchange erhalten"
    assert received_payload["type"] == PREPROCESSING_RK
    assert received_payload["payload"]["value"] == 120
    assert received_payload["payload"]["job"] == "preprocessing"
