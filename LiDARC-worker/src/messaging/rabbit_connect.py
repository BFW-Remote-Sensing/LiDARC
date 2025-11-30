import pika
from .rabbit_config import rabbitConfig

def create_connection() -> pika.BlockingConnection:
    creds = pika.PlainCredentials(rabbitConfig.username, rabbitConfig.password)
    params = pika.ConnectionParameters(
        host=rabbitConfig.host,
        port=rabbitConfig.port,
        virtual_host=rabbitConfig.vhost,
        credentials=creds,
        heartbeat=30,
        blocked_connection_timeout=300,
    )
    return pika.BlockingConnection(params)

def create_channel(conn: pika.BlockingConnection) -> pika.channel.Channel:
    ch = conn.channel()
    # Topologie kommt aus definitions.json → KEINE queue_declare / exchange_declare hier
    ch.basic_qos(prefetch_count=10)
    return ch
