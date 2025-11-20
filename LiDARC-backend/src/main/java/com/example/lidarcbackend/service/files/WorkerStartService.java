package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.RabbitProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.stereotype.Service;

@Service
public class WorkerStartService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public WorkerStartService(RabbitTemplate rabbitTemplate,
                              RabbitProperties rabbitProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = rabbitProperties;
    }


    public void startMetadataJob(JSONArray payload) {
        rabbitTemplate.convertAndSend(
                props.getJobExchange(),                       // → worker.job
                props.getRouting().getMetadataStart(),     // → worker.metadata.job.start
                payload
        );
    }

    public void startPreprocessingJob(JSONArray payload) {
        rabbitTemplate.convertAndSend(
                props.getJobExchange(),                       // immer worker.job
                props.getRouting().getPreprocessingStart(),
                payload
        );
    }

    public void startComparisonJob(JSONArray payload) {
        rabbitTemplate.convertAndSend(
                props.getJobExchange(),
                props.getRouting().getComparisonStart(),
                payload
        );
    }
}
