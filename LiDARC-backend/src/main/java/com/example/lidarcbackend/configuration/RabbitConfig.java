package com.example.lidarcbackend.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String WORKER_RESULTS_EXCHANGE = "worker-results";

    // queue for metadata worker results
    public static final String METADATA_WORKER_RESULTS_QUEUE = "worker_metadata_result";
    public static final String METADATA_WORKER_RESULTS_KEY = "worker.metadata.result";

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
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

    @Bean
    public TopicExchange workerResultsExchange() {
        return new TopicExchange(WORKER_RESULTS_EXCHANGE);
    }

    @Bean
    public Queue metadataWorkerResultsQueue() {
        return new Queue(METADATA_WORKER_RESULTS_QUEUE, true);
    }

    @Bean
    public Binding metadataBinding(@Qualifier("workerResultsExchange") TopicExchange workerResultsExchange,
                                   @Qualifier("metadataWorkerResultsQueue") Queue metadataWorkerResultsQueue) {
        return BindingBuilder.bind(metadataWorkerResultsQueue)
                .to(workerResultsExchange)
                .with(METADATA_WORKER_RESULTS_KEY);
    }


}
