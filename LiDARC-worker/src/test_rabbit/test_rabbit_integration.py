import json
import uuid
import time
import pika
from messaging.settings import settings
from messaging.result_publisher import ResultPublisher
# ===========================
# Configuration for Tests
# ===========================


TEST_QUEUE = "test.queue"
RESULT_EXCHANGE = settings.exchange_worker_results
RESULT_QUEUE = "test.result"
RESULT_ROUTING_KEY = "test.result"

# ===========================
# Tests
# ===========================

def test_can_connect_to_rabbit(rabbit_connection):
    """
    Minimaltest: Connection exists and is open.
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


def test_declared_test_queue_roundtrip(rabbit_channel):
    """
    Testet eine explizit deklarierte Queue mit festem Namen.
    So ähnlich wie deine worker.job Queues.
    """
    rabbit_channel.queue_declare(queue=TEST_QUEUE, durable=False, auto_delete=True)

    payload = {"job": "preprocess", "value": 42}
    body = json.dumps(payload).encode("utf-8")

    rabbit_channel.basic_publish(
        exchange="",
        routing_key=TEST_QUEUE,
        body=body,
        properties=pika.BasicProperties(
            content_type="application/json",
        ),
    )

    method, properties, received_body = rabbit_channel.basic_get(
        queue=TEST_QUEUE,
        auto_ack=True,
    )

    assert method is not None
    received_payload = json.loads(received_body.decode("utf-8"))
    assert received_payload["job"] == "preprocess"
    assert received_payload["value"] == 42


def test_result_exchange_routing(rabbit_channel, rabbit_connection):
    """
    Testet, ob über deinen Results-Exchange korrekt an eine Queue geroutet wird.
   """

    # Exchange und Queue declare (idempotent)
    # declare usually with definitions.json
    rabbit_channel.exchange_declare(
        exchange=RESULT_EXCHANGE,
        exchange_type="direct",
        durable=True,
    )
    rabbit_channel.queue_declare(queue=RESULT_QUEUE, durable=False, auto_delete=True)
    rabbit_channel.queue_bind(
        queue=RESULT_QUEUE,
        exchange=RESULT_EXCHANGE,
        routing_key=RESULT_ROUTING_KEY,
    )

    publisher = ResultPublisher
    publisher._conn = rabbit_connection
    publisher._channel =rabbit_channel

    payload = {"status": "OK"}
    body = json.dumps(payload).encode("utf-8")

   ## publisher._publish(RESULT_ROUTING_KEY, payload, "test")
    # Wenn du einen eigenen ResultPublisher hast, kannst du den hier statt basic_publish nutzen.
    rabbit_channel.basic_publish(
        exchange=RESULT_EXCHANGE,
        routing_key=RESULT_ROUTING_KEY,
        body=body,
        properties=pika.BasicProperties(
            content_type="application/json",
        ),
    )



    # ein bisschen pollen, weil Messages asynchron ankommen können
    message = None
    for _ in range(10):
        method, properties, received_body = rabbit_channel.basic_get(
            queue=RESULT_QUEUE,
            auto_ack=True,
        )
        if method:
            message = json.loads(received_body.decode("utf-8"))
            break
        time.sleep(0.1)

    assert message is not None, "Keine Nachricht über den Results-Exchange erhalten"
    assert message["status"] == "OK"