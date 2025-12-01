# src/test_rabbit/test_message_model.py

import json
import uuid
from datetime import datetime, timezone

from messaging.message_model import BaseMessage


def test_base_message_to_json_contains_all_fields():
    payload = {"job": "metadata", "value": 42}

    msg = BaseMessage(
        type="test",
        status="success",
        job_id=str(uuid.uuid4()),
        payload=payload,
    )

    as_json = msg.to_json()
    data = json.loads(as_json)

    assert data["type"] == "test"
    assert data["status"] == "success"
    assert data["payload"] == payload
    assert "job_id" in data
    assert "timestamp" in data


def test_base_message_timestamp_is_iso_datetime():
    msg = BaseMessage(
        type="test",
        status="success",
        job_id=str(uuid.uuid4()),
        payload={"foo": "bar"},
    )

    data = json.loads(msg.to_json())
    ts_str = data["timestamp"]

    parsed = datetime.fromisoformat(ts_str)
    assert isinstance(parsed, datetime)
    assert parsed.tzinfo is not None


def test_base_message_job_id_is_valid_uuid():
    msg = BaseMessage(
        type="test",
        status="success",
        job_id=str(uuid.uuid4()),
        payload={"job": "metadata"}
    )

    data = json.loads(msg.to_json())
    uuid_obj = uuid.UUID(data["job_id"])

    assert isinstance(uuid_obj, uuid.UUID)
