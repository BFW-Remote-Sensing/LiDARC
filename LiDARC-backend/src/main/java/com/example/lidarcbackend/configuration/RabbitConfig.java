package com.example.lidarcbackend.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
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
    public static final String METADATA_WORKER_RESULTS_QUEUE = "worker_result_metadata";
    public static final String METADATA_WORKER_RESULTS_KEY = "worker.result.metadata";

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
    public DirectExchange workerResultsExchange() {
        return new DirectExchange(WORKER_RESULTS_EXCHANGE);
    }

    @Bean
    public Queue metadataWorkerResultsQueue() {
        return new Queue(METADATA_WORKER_RESULTS_QUEUE, true);
    }

    @Bean
    public Binding metadataBinding(@Qualifier("workerResultsExchange") DirectExchange workerResultsExchange,
                                   @Qualifier("metadataWorkerResultsQueue") Queue metadataWorkerResultsQueue) {
        return BindingBuilder.bind(metadataWorkerResultsQueue)
                .to(workerResultsExchange)
                .with(METADATA_WORKER_RESULTS_KEY);
    }


}
