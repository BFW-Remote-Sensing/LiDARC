package com.example.lidarcbackend.service.files;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WorkerResultListener {

    @RabbitListener(queues = "${worker.queues.preprocessing-result}")
    public void handlePreprocessingResult(String message) {
        // Status aktualisieren, DB schreiben etc.
    }

    @RabbitListener(queues = "${worker.queues.comparison-result}")
    public void handleComparisonResult(String message) {
        // ...
    }

    @RabbitListener(queues = "${worker.queues.metadata-result}")
    public void handleMetadataResult(String message) {
        // ...
    }

}
