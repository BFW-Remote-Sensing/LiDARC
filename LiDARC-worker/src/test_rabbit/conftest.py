import pika
import pytest

from testcontainers.rabbitmq import RabbitMqContainer
from messaging.rabbit_connect import create_channel
from messaging.result_publisher import ResultPublisher


# ===========================
# Fixtures
# ===========================

@pytest.fixture(scope="session")
def rabbit_connection():
    with RabbitMqContainer("rabbitmq:3.9.10") as container:
        connection = pika.BlockingConnection(container.get_connection_params())

        yield connection
        connection.close()

@pytest.fixture()
def rabbit_channel(rabbit_connection):
    """
    Creates a new channel for every test.
    """
    ch = create_channel(rabbit_connection)
    yield ch
    ch.close()

@pytest.fixture()
def result_publisher(rabbit_connection, rabbit_channel):
    publisher = ResultPublisher()
    publisher._conn = rabbit_connection
    publisher._ch = rabbit_channel
    yield publisher