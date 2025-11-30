import os
import threading
from multiprocessing.pool import job_counter

import pytest
import pika
from numpy.lib.recfunctions import join_by

import preprocess.preprocess_worker as preprocess
from messaging.rabbit_config import rabbitConfig
from testcontainers.minio import MinioContainer
from testcontainers.rabbitmq import RabbitMqContainer
from minio import Minio

from messaging.rabbit_connect import create_rabbit_con_and_return_channel
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
        ch = create_rabbit_con_and_return_channel()
        rabbit_test_declarations(ch)
        yield ch
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

        credentials = pika.PlainCredentials(username=rabbitmq.username, password=rabbitmq.password)
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(host=rabbitmq.get_container_host_ip(), port=rabbitmq.get_exposed_port(5672), virtual_host=rabbitmq.vhost, credentials=credentials))
        ch = connection.channel()
        rabbit_test_declarations(ch)
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



@pytest.fixture()
def result_publisher(rabbitmq_ch):
    publisher = ResultPublisher(ch=rabbitmq_ch)
    yield publisher



def rabbit_test_declarations(ch):
    job_exchange_name = rabbitConfig.exchange_worker_job
    result_exchange_name = rabbitConfig.exchange_worker_results
    ch.exchange_declare(exchange=job_exchange_name, exchange_type="direct", durable=True)
    ch.queue_declare(queue=rabbitConfig.queue_preprocessing_job, durable=True)
    ch.queue_bind(queue=rabbitConfig.queue_preprocessing_job, exchange=job_exchange_name,
                  routing_key=rabbitConfig.routing_preprocessing_start)
    ch.exchange_declare(exchange=result_exchange_name, exchange_type="direct", durable=True)
    ch.queue_declare(queue=rabbitConfig.queue_preprocessing_result, durable=True)
    ch.queue_bind(queue=rabbitConfig.queue_preprocessing_result, exchange=result_exchange_name,
                  routing_key=rabbitConfig.routing_preprocessing_result)
