package com.example.lidarcbackend;

import com.example.lidarcbackend.configuration.RabbitConfig;
import com.example.lidarcbackend.service.files.MockPresignedUrlService;

import org.junit.jupiter.api.Assertions;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;


@Testcontainers
@SpringBootTest
public class RabbitBackendIntegrationTests {


    @Autowired
    RabbitTemplate rabbitTemplate;


    @MockitoBean
    private MockPresignedUrlService mockPresignedUrlService;

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("rabbitmq/definitions.json"),
                    "rabbitmq/definitions.json")
            .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "-rabbitmq_management load_definitions \"rabbitmq/definitions.json\"");


    //muss dynamisch generiert werden, damit spring korrekt zugreifen kann
    @DynamicPropertySource
    static void overrideSpringProps(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "testuser");
        registry.add("spring.rabbitmq.password", () -> "testpass");
    }


    @Test
    void sendMessageToTestQueueAndConsumeQueue() {
        String expected = "testMessage";
        rabbitTemplate.convertAndSend(RabbitConfig.TEST_EXCHANGE, RabbitConfig.TEST_RK, expected);
        Object message = rabbitTemplate.receiveAndConvert(RabbitConfig.TEST_QUEUE, 500);
        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }


    // ============================
    // JOB QUEUE BINDINGS
    // ============================

    @Test
    void testMetadataJobQueueBinding() {
        String expected = "testMessageMetadataJob";
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_METADATA_START_ROUTING_KEY,
                expected
        );

        Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.WORKER_METADATA_JOB_QUEUE, 1000);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }

    @Test
    void testPreprocessingJobQueueBinding() {
        String expected = "testMessagePreprocessingJob";
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_PREPROCESSING_START_ROUTING_KEY,
                expected
        );

        Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.WORKER_PREPROCESSING_JOB_QUEUE, 1000);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }

    @Test
    void testComparisonJobQueueBinding() {
        String expected = "testMessageComparisonJob";
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_JOB_EXCHANGE,
                RabbitConfig.WORKER_COMPARISON_START_ROUTING_KEY,
                expected
        );

        Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.WORKER_COMPARISON_JOB_QUEUE, 1000);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }


    // ============================
    // RESULT QUEUE BINDINGS
    // ============================

    @Test
    void testMetadataResultQueueBinding() {
        String expected = "testMessageMetadataResult";
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_RESULTS_EXCHANGE,
                RabbitConfig.WORKER_METADATA_RESULT_ROUTING_KEY,
                expected
        );

        Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.WORKER_METADATA_RESULT_QUEUE, 1000);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }

    @Test
    void testPreprocessingResultQueueBinding() {
        String expected = "testMessagePreprocessingResult";

        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_RESULTS_EXCHANGE,
                RabbitConfig.WORKER_PREPROCESSING_RESULT_ROUTING_KEY,
                expected
        );

        Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.WORKER_PREPROCESSING_RESULT_QUEUE, 1000);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }

    @Test
    void testComparisonResultQueueBinding() {
        String expected = "testMessageComparisonResult";
        rabbitTemplate.convertAndSend(
                RabbitConfig.WORKER_RESULTS_EXCHANGE,
                RabbitConfig.WORKER_COMPARISON_RESULT_ROUTING_KEY,
                expected
        );

        Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.WORKER_COMPARISON_RESULT_QUEUE, 1000);

        Assertions.assertNotNull(message);
        Assertions.assertEquals(expected, message);
    }
}




