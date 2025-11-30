package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkerStartService {

    private final RabbitTemplate rabbitTemplate;


    public WorkerStartService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }


    public void startMetadataJob(String payload) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,                       // → worker-job
                RabbitConfig.WORKER_METADATA_START_ROUTING_KEY,     // → worker.metadata.job.start
                payload
        );
    }

    public void startPreprocessingJob(String payload) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_PREPROCESSING_START_ROUTING_KEY,
                payload
        );
    }

    public void startComparisonJob(String payload) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_COMPARISON_START_ROUTING_KEY,
                payload
        );
    }
}
