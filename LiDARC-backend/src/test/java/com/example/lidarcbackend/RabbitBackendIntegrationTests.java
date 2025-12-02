package com.example.lidarcbackend;

import com.example.lidarcbackend.configuration.RabbitConfig;
import com.example.lidarcbackend.service.files.MockPresignedUrlService;

import com.example.lidarcbackend.service.files.WorkerStartService;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
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

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@Testcontainers
@SpringBootTest
public class RabbitBackendIntegrationTests {


    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    WorkerStartService jobStarterService;



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
        assertEquals(expected, message);
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
        assertEquals(expected, message);
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
        assertEquals(expected, message);
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
        assertEquals(expected, message);
    }

//
//    // ============================
//    // RESULT QUEUE BINDINGS
//    // ============================
//
//    @Test
//    void testMetadataResultQueueBinding() throws InterruptedException {
//        String expected = "testMessageMetadataResult";
//        rabbitTemplate.convertAndSend(
//                RabbitConfig.PYTHON_WORKER_RESULTS_EXCHANGE,
//                RabbitConfig.WORKER_METADATA_RESULT_ROUTING_KEY,
//                expected
//        );
//
//        Object message = rabbitTemplate.receiveAndConvert(
//                RabbitConfig.WORKER_METADATA_RESULT_QUEUE, 1000);
//        System.out.println(message);
//
//        Assertions.assertNotNull(message);
//        assertEquals(expected, message);
//    }
//
//    @Test
//    void testPreprocessingResultQueueBinding() {
//        String expected = "testMessagePreprocessingResult";
//
//        rabbitTemplate.convertAndSend(
//                RabbitConfig.PYTHON_WORKER_RESULTS_EXCHANGE,
//                RabbitConfig.WORKER_PREPROCESSING_RESULT_ROUTING_KEY,
//                expected
//        );
//
//        Object message = rabbitTemplate.receive(
//                RabbitConfig.WORKER_PREPROCESSING_RESULT_QUEUE, 2000);
//
//        Assertions.assertNotNull(message);
//        assertEquals(expected, message);
//    }
//
//    @Test
//    void testComparisonResultQueueBinding() {
//        String expected = "testMessageComparisonResult";
//        rabbitTemplate.convertAndSend(
//                RabbitConfig.PYTHON_WORKER_RESULTS_EXCHANGE,
//                RabbitConfig.WORKER_COMPARISON_RESULT_ROUTING_KEY,
//                expected
//        );
//
//        Object message = rabbitTemplate.receiveAndConvert(
//                RabbitConfig.WORKER_COMPARISON_RESULT_QUEUE, 1000);
//
//        Assertions.assertNotNull(message);
//        assertEquals(expected, message);
//    }

    @Test
    void testComparisonJobStarterService() {
        String  expected = "testMessageComparisonJobStarterService";
        jobStarterService.startComparisonJob(expected);

        Object message = rabbitTemplate.receiveAndConvert(RabbitConfig.WORKER_COMPARISON_JOB_QUEUE, 1000);

        Assertions.assertNotNull(message);
        assertEquals(expected, message);

    }

//    @Test
//    void testMetadataJobStarterService() {
//        String  expected = "testMessageComparisonJobStarterService";
//        jobStarterService.startMetadataJob(expected);
//
//        Object message = rabbitTemplate.receiveAndConvert(RabbitConfig.WORKER_METADATA_JOB_QUEUE, 1000);
//
//        Assertions.assertNotNull(message);
//        assertEquals(expected, message);
//
//    }

    @Test
    void testPreprocessorJobStarterService() {
        String  expected = "testMessageComparisonJobStarterService";
        jobStarterService.startPreprocessingJob(expected);

        Object message = rabbitTemplate.receiveAndConvert(RabbitConfig.WORKER_PREPROCESSING_JOB_QUEUE, 1000);

        Assertions.assertNotNull(message);
        assertEquals(expected, message);

    }

    @Test
    void printRabbitTopology() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost());
        factory.setPort(rabbit.getAmqpPort());
        factory.setUsername("admin");
        factory.setPassword("admin");

        Connection connection = factory.newConnection();
        Channel ch = connection.createChannel();

        System.out.println("=== EXCHANGES ===");
        for (String ex : List.of("worker-job", "worker-results")) {
            try {
                ch.exchangeDeclarePassive(ex);
                System.out.println("  ✔ " + ex);
            } catch (IOException e) {
                System.out.println("  ✘ MISSING: " + ex);
            }
        }

        System.out.println("=== QUEUES ===");
        for (String q : List.of(
                "worker.metadata.result",
                "worker.preprocessing.result",
                "worker.comparison.result"
        )) {
            try {
                ch.queueDeclarePassive(q);
                System.out.println("  ✔ " + q);
            } catch (IOException e) {
                System.out.println("  ✘ MISSING: " + q);
            }
        }
        String testMessage = "testMessage";
        ch.basicPublish(RabbitConfig.PYTHON_WORKER_RESULTS_EXCHANGE, RabbitConfig.WORKER_COMPARISON_RESULT_ROUTING_KEY, null, testMessage.getBytes());

        // Konsumieren
        GetResponse response = ch.basicGet(RabbitConfig.WORKER_COMPARISON_RESULT_QUEUE, true);

        if (response == null) {
            System.out.println("Binding fehlt: keine Nachricht in Queue '" + RabbitConfig.WORKER_COMPARISON_RESULT_ROUTING_KEY + "' angekommen!");
        } else {
            String body = new String(response.getBody());
            assertEquals(testMessage, body);
            System.out.println("✔ Binding OK für " + RabbitConfig.WORKER_COMPARISON_RESULT_ROUTING_KEY);
        }

        String testMessage2 = "testMessage";
        ch.basicPublish(RabbitConfig.PYTHON_WORKER_RESULTS_EXCHANGE, "test", null, testMessage.getBytes());

        // Konsumieren
        GetResponse response2 = ch.basicGet("test.queue", true);

        if (response2 == null) {
            System.out.println("Binding fehlt: keine Nachricht in Queue '" + RabbitConfig.WORKER_COMPARISON_RESULT_ROUTING_KEY + "' angekommen!");
        } else {
            String body = new String(response2.getBody());
            assertEquals(testMessage, body);
            System.out.println("✔ Binding OK für " + "test");
        }
    }
}




