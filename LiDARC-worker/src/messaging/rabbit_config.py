import os
from dataclasses import dataclass

@dataclass
class RabbitMQConfig:
    #if env variable is set, the env var would be returned, otherwise the second (default) value
    host: str = os.getenv("RABBITMQ_HOST", "localhost")
    port: int = int(os.getenv("RABBITMQ_PORT", "5672"))
    username: str = os.getenv("RABBITMQ_USER", "admin")
    password: str = os.getenv("RABBITMQ_PASS", "admin")
    vhost: str = os.getenv("RABBITMQ_VHOST", "/")
    prefetch_count: int = int(os.getenv("RABBITMQ_PREFETCH", "10"))

    # Topologie-names (have to be correspondent to definitions.json)
    exchange_worker_job: str = "worker-job"
    queue_preprocessing: str = "worker.preprocessing.job"
    queue_comparison_job: str = "worker.comparison.job"
    queue_metadata_job: str = "worker.metadata.job"

    routing_preprocessing_start: str = "worker.preprocessing.job.start"
    routing_comparison_start: str = "worker.comparison.job.start"
    routing_metadata_start: str = "worker.metadata.job.start"


    exchange_worker_results: str = "worker-results"
    queue_preprocessing_result: str = "worker.preprocessing.result"
    queue_comparison_result: str = "worker.comparison.result"
    queue_metadata_result: str = "worker.metadata.result"

    routing_preprocessing_result: str = "worker.preprocessing.result"
    routing_comparison_result: str = "worker.comparison.result"
    routing_metadata_result: str = "worker.metadata.result"



#exported topology --> use "rabbitConfig.*"
rabbitConfig = RabbitMQConfig()
