package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.RabbitConfig;
import com.example.lidarcbackend.service.comparisons.IComparisonService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WorkerResultListener {

    private final IMetadataService metadataService;
    private final IComparisonService comparisonService;


    public WorkerResultListener(IMetadataService metadataService, IComparisonService comparisonService) {
        this.metadataService = metadataService;
        this.comparisonService = comparisonService;
    }

    //TODO: HANDLE EXCEPTIONS CORRECTLY FOR EXAMPLE METADATA -> Capture Year < 1900 --> Exception
    //TODO
    // set Input Parameter for every method
    @RabbitListener(queues = RabbitConfig.WORKER_PREPROCESSING_RESULT_QUEUE)
    public void handlePreprocessingResult(Map<String, Object> result) {
        comparisonService.processPreprocessingResult(result);
    }

    @RabbitListener(queues = RabbitConfig.WORKER_COMPARISON_RESULT_QUEUE)
    public void handleComparisonResult(Map<String, Object> result) {
        comparisonService.processComparisonResult(result);
    }

    @RabbitListener(queues = RabbitConfig.WORKER_METADATA_RESULT_QUEUE)
    public void handleMetadataResult(Map<String, Object> result) {
        metadataService.processMetadata(result);
    }

    @RabbitListener(queues = RabbitConfig.WORKER_CHUNKING_COMPARISON_RESULT_QUEUE)
    public void handleChunkingComparisonResult(Map<String, Object> result) {
        comparisonService.saveVisualizationComparison(result);
    }

}
