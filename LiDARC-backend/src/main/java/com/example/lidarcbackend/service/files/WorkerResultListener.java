package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WorkerResultListener {

    private final IMetadataService metadataService;

    public WorkerResultListener(IMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    //TODO
    // set Input Parameter for every method
    @RabbitListener(queues = RabbitConfig.WORKER_PREPROCESSING_RESULT_QUEUE)
    public void handlePreprocessingResult(String message) {
        // Status aktualisieren, DB schreiben etc.

    }

    @RabbitListener(queues = RabbitConfig.WORKER_COMPARISON_RESULT_QUEUE)
    public void handleComparisonResult(String message) {
        // ...
    }

    @RabbitListener(queues = RabbitConfig.WORKER_METADATA_RESULT_QUEUE)
    public void handleMetadataResult(Map<String, Object> result) {
        metadataService.processMetadata(result);
    }

}
