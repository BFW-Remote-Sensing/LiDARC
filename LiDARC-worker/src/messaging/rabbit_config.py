import os
from dataclasses import dataclass

@dataclass
class RabbitMQConfig:
    #if env variable is set, the env var would be returned, otherwise the second (default) value
    host: str = None
    port: int = None
    username: str = None
    password: str = None
    vhost: str = None
    prefetch_count: int = None

    # Topologie-names (have to be correspondent to definitions.json)
    #
    exchange_worker_job: str = "worker-job"
    # (Java --> Python)
    queue_preprocessing_job: str = "worker.preprocessing.job"
    queue_comparison_job: str = "worker.comparison.job"
    queue_chunking_comparison_job: str = "worker.chunking.comparison.job"
    queue_metadata_job: str = "worker.metadata.job"
    # listener rk for jobs
    routing_preprocessing_start: str = "worker.preprocessing.job.start"
    routing_comparison_start: str = "worker.comparison.job.start"
    routing_chunking_comparison_start: str = "worker.chunking.comparison.job.start"
    routing_metadata_start: str = "worker.metadata.job.start"


    exchange_worker_results: str = "worker-results"
    # (Python --> Java)
    queue_preprocessing_result: str = "worker.preprocessing.result"
    queue_comparison_result: str = "worker.comparison.result"
    queue_chunking_comparison_result: str = "worker.chunking.comparison.result"
    queue_metadata_result: str = "worker.metadata.result"

    routing_preprocessing_result: str = "worker.preprocessing.result"
    routing_comparison_result: str = "worker.comparison.result"
    routing_chunking_comparison_result: str = "worker.chunking.comparison.result"
    routing_metadata_result: str = "worker.metadata.result"

    def __post_init__(self):
        if self.host is None:
            self.host = os.getenv("RABBITMQ_HOST", "rabbitmq")
        if self.port is None:
            self.port = int(os.getenv("RABBITMQ_PORT", "5672"))
        if self.username is None:
            self.username = os.getenv("RABBITMQ_USER", "admin")
        if self.password is None:
            self.password = os.getenv("RABBITMQ_PASSWORD", "admin")
        if self.vhost is None:
            self.vhost = os.getenv("RABBITMQ_VHOST", "/")
        if self.prefetch_count is None:
            self.prefetch_count = int(os.getenv("RABBITMQ_PREFETCH", "0"))

def get_rabbitmq_config():
    return RabbitMQConfig()

#exported topology --> use "rabbitConfig.*"
#rabbitConfig = RabbitMQConfig()
