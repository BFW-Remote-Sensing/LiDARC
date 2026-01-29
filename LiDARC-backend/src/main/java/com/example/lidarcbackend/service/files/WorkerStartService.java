package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.RabbitConfig;
import com.example.lidarcbackend.model.DTO.StartChunkingJobDto;
import com.example.lidarcbackend.model.DTO.StartComparisonJobDto;
import com.example.lidarcbackend.model.DTO.StartMetadataJobDto;
import com.example.lidarcbackend.model.DTO.StartPreProcessJobDto;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkerStartService {

    private final RabbitTemplate rabbitTemplate;


    public WorkerStartService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }


    public void startMetadataJob(StartMetadataJobDto startMetadataJobDto) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,                       // → worker-job
                RabbitConfig.WORKER_METADATA_START_ROUTING_KEY,     // → worker.metadata.job.start
                startMetadataJobDto
        );
    }

    public void startPreprocessingJob(StartPreProcessJobDto startPreProcessJobDto) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_PREPROCESSING_START_ROUTING_KEY,
                startPreProcessJobDto
        );
    }

    public void startComparisonJob(StartComparisonJobDto startComparisonJobDto) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_COMPARISON_START_ROUTING_KEY,
                startComparisonJobDto
        );
    }

    public void startChunkingComparisonJob(StartChunkingJobDto startChunkingJobDto) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_CHUNKING_COMPARISON_START_ROUTING_KEY,
            startChunkingJobDto
        );
    }
}
