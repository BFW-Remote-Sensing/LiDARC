package com.example.lidarcbackend.service.files;

import com.example.lidarcbackend.configuration.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RabbitMQListenerService {

    private IMetadataService metadataService;

    public RabbitMQListenerService(IMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @RabbitListener(queues = RabbitConfig.METADATA_WORKER_RESULTS_QUEUE)
    public void handleMetadataResult(Map<String, Object> result) {
        metadataService.processMetadata(result);
    }

}
