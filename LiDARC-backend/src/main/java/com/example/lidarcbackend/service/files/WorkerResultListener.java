package com.example.lidarcbackend.service.files;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.stereotype.Component;

@Component
public class WorkerResultListener {

    @RabbitListener(queues = "${worker.queues.preprocessing-result}")
    public void handlePreprocessingResult(JSONArray payload) {
        // Status aktualisieren, DB schreiben etc.
    }

    @RabbitListener(queues = "${worker.queues.comparison-result}")
    public void handleComparisonResult(JSONArray payload) {
        // ...
    }

    @RabbitListener(queues = "${worker.queues.metadata-result}")
    public void handleMetadataResult(JSONArray payload) {
        // ...
    }

}
