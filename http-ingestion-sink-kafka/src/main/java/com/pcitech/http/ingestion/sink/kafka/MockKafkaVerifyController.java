package com.pcitech.http.ingestion.sink.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@RestController
@Profile("e2e")
@RequestMapping("/mock/_test/kafka")
public class MockKafkaVerifyController {

    @Value("${ingestion.external-kafka.bootstrap-servers:}")
    private String bootstrapServers;

    @GetMapping("/count")
    public Map<String, Object> count(@RequestParam String topic) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return Map.of("ok", false, "reason", "EXTERNAL_KAFKA_BOOTSTRAP_SERVERS not configured");
        }
        if (topic == null || topic.isBlank()) {
            return Map.of("ok", false, "reason", "topic is required");
        }

        String servers = bootstrapServers.replace("PLAINTEXT://", "");
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-verify-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        TopicPartition partition = new TopicPartition(topic, 0);
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));

            long total = 0;
            int idlePolls = 0;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline && idlePolls < 4) {
                var batch = consumer.poll(Duration.ofMillis(500));
                if (batch.isEmpty()) {
                    idlePolls++;
                } else {
                    idlePolls = 0;
                    total += batch.count();
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            response.put("topic", topic);
            response.put("count", total);
            return response;
        } catch (Exception ex) {
            return Map.of("ok", false, "reason", ex.getMessage());
        }
    }
}
