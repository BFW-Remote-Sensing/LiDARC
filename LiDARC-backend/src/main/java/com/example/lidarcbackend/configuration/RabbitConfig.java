package com.example.lidarcbackend.configuration;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    //TEST EXCHANGE, QUEUE, RK
    public static final String TEST_EXCHANGE = "test-exchange";
    public static final String TEST_QUEUE = "test.queue";
    public static final String TEST_RK = "test";

    // ============================
    // EXCHANGES
    // ============================
    public static final String WORKER_JOB_EXCHANGE = "worker-job";
    public static final String PYTHON_WORKER_RESULTS_EXCHANGE = "worker-results";


    // ============================
    // JOB QUEUES (Java → Python)
    // ============================
    public static final String WORKER_METADATA_JOB_QUEUE = "worker.metadata.job";
    public static final String WORKER_PREPROCESSING_JOB_QUEUE = "worker.preprocessing.job";
    public static final String WORKER_COMPARISON_JOB_QUEUE = "worker.comparison.job";


    // ============================
    // RESULT QUEUES (Python → Java)
    // ============================
    public static final String WORKER_METADATA_RESULT_QUEUE = "worker.metadata.result";
    public static final String WORKER_PREPROCESSING_RESULT_QUEUE = "worker.preprocessing.result";
    public static final String WORKER_COMPARISON_RESULT_QUEUE = "worker.comparison.result";


    // ============================
    // ROUTING KEYS — Jobs (Start)
    // ============================
    public static final String WORKER_METADATA_START_ROUTING_KEY = "worker.metadata.job.start";
    public static final String WORKER_PREPROCESSING_START_ROUTING_KEY = "worker.preprocessing.job.start";
    public static final String WORKER_COMPARISON_START_ROUTING_KEY = "worker.comparison.job.start";


    // ============================
    // ROUTING KEYS — Results
    // (Used by Python Workers)
    // ============================
    public static final String WORKER_METADATA_RESULT_ROUTING_KEY = "worker.metadata.result.start";
    public static final String WORKER_PREPROCESSING_RESULT_ROUTING_KEY = "worker.preprocessing.result";
    public static final String WORKER_COMPARISON_RESULT_ROUTING_KEY = "worker.comparison.result";




    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

}
