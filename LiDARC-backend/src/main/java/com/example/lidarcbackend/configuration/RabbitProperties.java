package com.example.lidarcbackend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "worker")
public class RabbitProperties {
    private String jobExchange;   // immer der Job-Exchange
    private Routing routing;
    private Queues queues;


    // --- Routing Keys for Job-Starts ---
    @Setter @Getter
    public static class Routing {
        private String metadataStart;
        private String preprocessingStart;
        private String comparisonStart;


    }

    // --- Result-Queues, backend listens to ---
    @Setter @Getter
    public static class Queues {
        private String metadataResult;
        private String preprocessingResult;
        private String comparisonResult;

    }
}


