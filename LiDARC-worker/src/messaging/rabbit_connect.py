import pika
from .topology import topology

def create_connection() -> pika.BlockingConnection:
    creds = pika.PlainCredentials(topology.username, topology.password)
    params = pika.ConnectionParameters(
        host=topology.host,
        port=topology.port,
        virtual_host=topology.vhost,
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
