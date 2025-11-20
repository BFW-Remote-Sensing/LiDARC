# worker_python/messaging/message_model.py
from dataclasses import dataclass
from typing import Any, Dict
import json
import uuid
from datetime import datetime, timezone

#feel free to change this model as you wish/need
@dataclass
class BaseMessage:
    type: str
    version: str
    job_id: str
    payload: Dict[str, Any]

    def to_json(self) -> bytes:
        return json.dumps({
            "type": self.type,
            "version": self.version, #probably not necessary
            "job_id": self.job_id,
            "payload": self.payload,
            "timestamp": datetime.now(timezone.utc),
        }).encode("utf-8")

    @staticmethod
    def from_json(body: bytes) -> "BaseMessage":
        data = json.loads(body.decode("utf-8"))
        return BaseMessage(
            type=data.get("type", "unknown"),
            version=data.get("version", "1"),
            job_id=data.get("job_id", str(uuid.uuid4())),
            payload=data.get("payload", {}),
        )
