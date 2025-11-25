import os
import threading

import pytest
import pika
import preprocess.preprocess_worker as preprocess
import metadata.metadata_worker as metadata
from testcontainers.minio import MinioContainer
from testcontainers.rabbitmq import RabbitMqContainer

minio = MinioContainer("quay.io/minio/minio:latest")
rabbitmq = RabbitMqContainer("rabbitmq:3.12-management")

@pytest.fixture(scope="module", autouse=True)
def minio_client(request, las_with_header_module_scope, small_las_file):
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

    client.fput_object(bucket_name="basebucket",
                      object_name="small.las",
                      file_path=small_las_file)

    las_file_path = las_with_header_module_scope(with_crs_header=True)
    client.fput_object(bucket_name="basebucket",
                       object_name="metadata_test.las",
                       file_path=las_file_path)

    objects = list(client.list_objects("basebucket", recursive=True))
    assert len(objects) == 2
    yield client

@pytest.fixture(scope="module", autouse=True)
def rabbitmq_ch(request):
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


    ch.queue_declare(queue="worker_metadata_job", durable=True)
    ch.exchange_declare(exchange="worker-job", exchange_type="topic")
    ch.queue_bind(queue="worker_metadata_job", exchange="worker-job", routing_key="worker.metadata.job.start")
    ch.queue_declare(queue="worker_metadata_result", durable=True)
    ch.exchange_declare(exchange="worker-results", exchange_type="topic")
    ch.queue_bind(queue="worker_metadata_result", exchange="worker-results", routing_key="worker.metadata.result")
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

@pytest.fixture
def run_metadata_worker(rabbitmq_metadata_ch, minio_client):
    thread = threading.Thread(target=metadata.main(), daemon=True)
    thread.start()
    yield thread


