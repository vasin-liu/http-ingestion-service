package com.pcitech.http.ingestion.sink.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.sink.RecordSink;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Component
public class KafkaRecordSink implements RecordSink {

    private final String bootstrapServers;
    private final ObjectMapper objectMapper;
    private volatile KafkaProducer<String, String> producer;

    public KafkaRecordSink(
            @Value("${ingestion.external-kafka.bootstrap-servers:}") String bootstrapServers,
            ObjectMapper objectMapper
    ) {
        this.bootstrapServers = bootstrapServers;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supportsType(String type) {
        return "kafka".equalsIgnoreCase(type);
    }

    @Override
    public boolean isAvailable() {
        return bootstrapServers != null && !bootstrapServers.isBlank();
    }

    @Override
    public int write(List<Map<String, Object>> records, RuntimeConnectorConfig.SinkSettings sink) throws Exception {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        if (sink == null || sink.topic() == null || sink.topic().isBlank()) {
            throw new IllegalArgumentException("Kafka topic is required");
        }
        if (!"json".equalsIgnoreCase(sink.serialization())) {
            throw new IllegalArgumentException("Only json serialization is supported for Kafka sink");
        }

        String keyField = sink.keys() == null || sink.keys().isEmpty() ? null : sink.keys().get(0);
        KafkaProducer<String, String> kafkaProducer = producer();
        int written = 0;
        for (Map<String, Object> record : records) {
            String key = keyField == null ? null : stringify(record.get(keyField));
            String payload = objectMapper.writeValueAsString(record);
            try {
                kafkaProducer.send(new ProducerRecord<>(sink.topic(), key, payload)).get();
                written++;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Kafka publish interrupted", ex);
            } catch (ExecutionException ex) {
                throw new IllegalStateException("Kafka publish failed", ex.getCause());
            }
        }
        kafkaProducer.flush();
        return written;
    }

    private static String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private KafkaProducer<String, String> producer() {
        if (producer == null) {
            synchronized (this) {
                if (producer == null) {
                    Properties properties = new Properties();
                    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                    properties.put(ProducerConfig.ACKS_CONFIG, "all");
                    producer = new KafkaProducer<>(properties);
                }
            }
        }
        return producer;
    }
}
