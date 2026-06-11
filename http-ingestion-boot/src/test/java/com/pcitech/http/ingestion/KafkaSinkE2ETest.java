package com.pcitech.http.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.service.SyncService;
import com.pcitech.http.ingestion.support.AbstractIntegrationE2ETest;
import com.pcitech.http.ingestion.support.ConnectorConfigFactory;
import com.pcitech.http.ingestion.support.E2EJobAwait;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KafkaSinkE2ETest extends AbstractIntegrationE2ETest {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.1");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    @RegisterExtension
    static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerKafka(DynamicPropertyRegistry registry) {
        registry.add("ingestion.external-kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void stubUsers() {
        WIREMOCK.stubFor(get(urlMatching("/users.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]")));
    }

    @Test
    void wireMockPull_publishesRecordsToKafkaTopic() throws Exception {
        String topic = "ingest.e2e.users." + UUID.randomUUID();
        createTopic(topic);
        JsonNode config = ConnectorConfigFactory.kafkaWireMockUsersConfig(
                objectMapper,
                "http://localhost:" + WIREMOCK.getPort(),
                topic
        );
        connectorService.create(new ConnectorRequestDto("e2e-kafka-users", "E2E Kafka Users", "pull", config));
        connectorService.publish("e2e-kafka-users");

        JobRun job = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync("e2e-kafka-users", SyncService.SyncOptions.full()));
        assertThat(job.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(job.recordsOk()).isEqualTo(2);

        try (KafkaConsumer<String, String> consumer = consumer()) {
            assertThat(readRecords(consumer, topic, 2)).hasSize(2);
        }
    }

    private void createTopic(String topic) throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(properties)) {
            try {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
            } catch (ExecutionException ex) {
                if (!(ex.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) {
                    throw ex;
                }
            }
        }
    }

    private static KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-e2e-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private static List<ConsumerRecord<String, String>> readRecords(
            KafkaConsumer<String, String> consumer,
            String topic,
            int expected
    ) {
        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(List.of(partition));
        consumer.seekToBeginning(List.of(partition));

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (records.size() < expected && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }
}
