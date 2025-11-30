# worker_python/messaging/result_publisher.py
import uuid
import pika

from .message_model import BaseMessage
from .rabbit_connect import create_connection, create_channel
from .rabbit_config import rabbitConfig

class ResultPublisher:
    def __init__(self, conn=None, ch=None):
        # check if connection comes from outside --> testcases
        self._external_conn = conn is not None or ch is not None

        self._conn = conn or create_connection()
        self._ch = ch or create_channel(self._conn)

    def _publish(self, routing_key: str, payload: dict, msg_type: str):
        msg = BaseMessage(
            type=msg_type,
            version="1",
            job_id=str(uuid.uuid4()),
            payload=payload,
        )
        self._ch.basic_publish(
            exchange=rabbitConfig.exchange_worker_results,
            routing_key=routing_key,
            body=msg.to_json(),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,  # persistent
            ),
        )

    def publish_preprocessing_result(self, payload: dict):
        self._publish(rabbitConfig.routing_preprocessing_result, payload, rabbitConfig.routing_preprocessing_result)

    def publish_comparison_result(self, payload: dict):
        self._publish(rabbitConfig.routing_comparison_result, payload, rabbitConfig.routing_comparison_result)

    def publish_metadata_result(self, payload: dict):
        self._publish(rabbitConfig.routing_metadata_result, payload, rabbitConfig.routing_metadata_result)

    def close(self):
        # if connection is done externally, connection is not closed here
        if not self._external_conn:
            if self._ch and self._ch.is_open:
                self._ch.close()
            if self._conn and self._conn.is_open:
                self._conn.close()
