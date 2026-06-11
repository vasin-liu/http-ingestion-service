package com.pcitech.http.ingestion.sink.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KafkaRecordSinkTestcontainersTest {

    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.1");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    @org.junit.jupiter.api.Test
    void publishesJsonRecordsWithMessageKey() throws Exception {
        String topic = "ingest.test.users." + UUID.randomUUID();
        createTopic(topic);
        KafkaRecordSink sink = new KafkaRecordSink(KAFKA.getBootstrapServers(), new ObjectMapper());
        RuntimeConnectorConfig.SinkSettings settings = new RuntimeConnectorConfig.SinkSettings(
                "kafka",
                null,
                null,
                List.of("id"),
                null,
                500,
                topic,
                "json"
        );

        int written = sink.write(
                List.of(
                        Map.of("id", 1L, "name", "Alice"),
                        Map.of("id", 2L, "name", "Bob")
                ),
                settings
        );

        assertThat(written).isEqualTo(2);

        try (KafkaConsumer<String, String> consumer = consumer()) {
            List<ConsumerRecord<String, String>> records = readRecords(consumer, topic, 2);
            assertThat(records.get(0).key()).isEqualTo("1");
            assertThat(records.get(1).key()).isEqualTo("2");
            assertThat(records.get(0).value()).contains("\"name\":\"Alice\"");
            assertThat(records.get(1).value()).contains("\"name\":\"Bob\"");
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
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-sink-test-" + UUID.randomUUID());
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
        assertThat(records).hasSize(expected);
        return records;
    }
}
