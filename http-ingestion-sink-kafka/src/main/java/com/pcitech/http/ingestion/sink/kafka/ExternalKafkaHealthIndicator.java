package com.pcitech.http.ingestion.sink.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Component("externalKafka")
public class ExternalKafkaHealthIndicator implements HealthIndicator {

    @Value("${ingestion.external-kafka.bootstrap-servers:}")
    private String bootstrapServers;

    @Override
    public Health health() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return Health.unknown().withDetail("reason", "EXTERNAL_KAFKA_BOOTSTRAP_SERVERS not configured").build();
        }
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        try (AdminClient admin = AdminClient.create(properties)) {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);
            return Health.up()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("clusterId", clusterId)
                    .build();
        } catch (Exception ex) {
            return Health.unknown()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("reason", ex.getMessage())
                    .build();
        }
    }
}
