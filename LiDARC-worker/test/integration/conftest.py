import os
import threading

import pytest
import pika
import preprocess.preprocess_worker as preprocess
from testcontainers.minio import MinioContainer
from testcontainers.rabbitmq import RabbitMqContainer
from minio import Minio

from messaging.result_publisher import ResultPublisher


def running_in_ci_mode():
    return os.getenv("CI") == "true" or os.getenv("GITLAB_CI") == "true"


@pytest.fixture(scope="module", autouse=True)
def minio_client(request, very_small_las_file):
    running_in_ci = running_in_ci_mode()
    if running_in_ci:
        endpoint = os.getenv("MINIO_ENDPOINT")
        access_key = os.getenv("MINIO_ACCESS_KEY")
        secret_key = os.getenv("MINIO_SECRET_KEY")

        client = Minio(
            endpoint,
            access_key=access_key,
            secret_key=secret_key,
            secure=False
        )
        if not client.bucket_exists("basebucket"):
            client.make_bucket("basebucket")
        def upload_file(file_path, object_name="small.las"):
            client.fput_object(bucket_name="basebucket",
                               object_name=object_name,
                               file_path=file_path)

        yield client, upload_file
        return
    else:
        minio = MinioContainer("registry.reset.inso-w.at/pub/docker/chainguard/minio:latest")
        minio.start()

        def teardown():
            minio.stop()

        request.addfinalizer(teardown)
        os.environ["MINIO_ACCESS_KEY"] = minio.access_key
        os.environ["MINIO_SECRET_KEY"] = minio.secret_key
        os.environ["MINIO_ENDPOINT"] = f"{minio.get_container_host_ip()}:{minio.get_exposed_port(9000)}"
        client = minio.get_client()
        if not client.bucket_exists("basebucket"):
            client.make_bucket("basebucket")
        def upload_file(file_path, object_name="small.las"):
            client.fput_object(bucket_name="basebucket",
                           object_name=object_name,
                           file_path=file_path)

            objects = list(client.list_objects("basebucket", recursive=True))
            assert len(objects) > 0, "Expected atleast one object in bucket after upload"
        yield client, upload_file

@pytest.fixture(scope="module", autouse=True)
def rabbitmq_ch(request):
    running_in_ci = running_in_ci_mode()
    if running_in_ci:
        username = os.getenv("RABBITMQ_USER")
        password = os.getenv("RABBITMQ_PASSWORD")
        host = os.getenv("RABBITMQ_HOST")
        port = os.getenv("RABBITMQ_PORT")
        vhost = os.getenv("RABBITMQ_VHOST")
        exchange_name = os.getenv("EXCHANGE_NAME")
        credentials = pika.PlainCredentials(username=username, password=password)
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(host=host, port=port, virtual_host=vhost, credentials=credentials))
        ch = connection.channel()
        ch.queue_declare(queue="preprocessing.job", durable=True)
        ch.exchange_declare(exchange=exchange_name, exchange_type="topic")
        ch.queue_bind(queue="preprocessing.job", exchange=exchange_name, routing_key="preprocessing.job") #TODO: Fix the messaging in future or make it independent of real setup
        ch.queue_declare(queue="preprocessing.result", durable=True)
        ch.queue_bind(queue="preprocessing.result", exchange=exchange_name, routing_key="job.preprocessor.create")
        yield ch, connection
    else:
        rabbitmq = RabbitMqContainer("rabbitmq:3.12-management")
        rabbitmq.start()

        def teardown():
            rabbitmq.stop()

        request.addfinalizer(teardown)
        rabbitmq.get_connection_params()
        os.environ["RABBITMQ_USER"]= rabbitmq.username
        os.environ["RABBITMQ_PASSWORD"] = rabbitmq.password
        os.environ["RABBITMQ_HOST"] = rabbitmq.get_container_host_ip()
        os.environ["RABBITMQ_PORT"] = str(rabbitmq.get_exposed_port(5672))
        os.environ["RABBITMQ_VHOST"] =  rabbitmq.vhost
        os.environ["EXCHANGE_NAME"] = "worker.job"

        credentials = pika.PlainCredentials(username=rabbitmq.username, password=rabbitmq.password)
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(host=rabbitmq.get_container_host_ip(), port=rabbitmq.get_exposed_port(5672), virtual_host=rabbitmq.vhost, credentials=credentials))
        ch = connection.channel()
        ch.queue_declare(queue="preprocessing.job", durable=True)
        ch.exchange_declare(exchange="worker.job", exchange_type="topic")
        ch.queue_bind(queue="preprocessing.job", exchange="worker.job", routing_key="preprocessing.job") #TODO: Fix the messaging in future or make it independent of real setup
        ch.queue_declare(queue="preprocessing.result", durable=True)
        ch.queue_bind(queue="preprocessing.result", exchange="worker.job", routing_key="job.preprocessor.create")
        yield ch

@pytest.fixture(scope="function", autouse=True)
def setup_bucket():
    pass
    #client = minio.get_client()
    #if not client.bucket_exists("basebucket"):
    #    return
    #TODO: Teardown if needed??

@pytest.fixture
def run_preprocess_worker(rabbitmq_ch, minio_client):
    thread = threading.Thread(target=preprocess.main(), daemon=True)
    thread.start()
    yield thread

@pytest.fixture(scope="function")
def rabbit_channel(rabbitmq_ch):
    connection, channel = rabbitmq_ch
    yield channel



@pytest.fixture()
def result_publisher(rabbitmq_ch):
    ch, connection = rabbitmq_ch
    publisher = ResultPublisher(conn=connection, ch=ch)
    yield publisher

