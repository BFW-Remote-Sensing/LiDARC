# worker_python/messaging/connection.py
import pika
from settings import settings

def create_connection() -> pika.BlockingConnection:
    creds = pika.PlainCredentials(settings.username, settings.password)
    params = pika.ConnectionParameters(
        host=settings.host,
        port=settings.port,
        virtual_host=settings.vhost,
        credentials=creds,
        heartbeat=30,
        blocked_connection_timeout=300,
    )
    return pika.BlockingConnection(params)

def create_channel(conn: pika.BlockingConnection) -> pika.channel.Channel:
    ch = conn.channel()
    # Topologie kommt aus definitions.json → KEINE queue_declare / exchange_declare hier
    ch.basic_qos(prefetch_count=settings.prefetch)
    return ch
