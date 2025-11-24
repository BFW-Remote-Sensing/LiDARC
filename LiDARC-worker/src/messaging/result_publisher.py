# worker_python/messaging/result_publisher.py
import uuid

import pika

from .message_model import BaseMessage
from .rabbit_connect import create_connection, create_channel
from .settings import settings

class ResultPublisher:
    def __init__(self):
        self._conn = create_connection()
        self._ch = create_channel(self._conn)

    def _publish(self, routing_key: str, payload: dict, msg_type: str):
        msg = BaseMessage(
            type=msg_type,
            version="1",
            job_id=str(uuid.uuid4()),
            payload=payload,
        )
        self._ch.basic_publish(
            exchange=settings.exchange_results,
            routing_key=routing_key,
            body=msg.to_json(),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,  # persistent
            ),
        )

    def publish_preprocessing_result(self, payload: dict):
        self._publish(settings.routing_preprocessing_result, payload, "worker.preprocessing.result")

    def publish_comparison_result(self, payload: dict):
        self._publish(settings.routing_comparison_result, payload, "worker.comparison.result")

    def publish_metadata_result(self, payload: dict):
        self._publish(settings.routing_metadata_result, payload, "worker.metadata.result")

    def close(self):
        if self._ch and self._ch.is_open:
            self._ch.close()
        if self._conn and self._conn.is_open:
            self._conn.close()
