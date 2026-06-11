package com.pcitech.http.ingestion.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;

@Component("externalPg")
public class ExternalPgHealthIndicator implements HealthIndicator {

    @Value("${ingestion.external-pg.url:}")
    private String jdbcUrl;

    @Value("${ingestion.external-pg.username:}")
    private String username;

    @Value("${ingestion.external-pg.password:}")
    private String password;

    @Override
    public Health health() {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return Health.unknown().withDetail("reason", "EXTERNAL_PG_URL not configured").build();
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            return connection.isValid(3)
                    ? Health.up().withDetail("url", mask(jdbcUrl)).build()
                    : Health.unknown().withDetail("url", mask(jdbcUrl)).withDetail("reason", "validation failed").build();
        } catch (Exception ex) {
            return Health.unknown()
                    .withDetail("url", mask(jdbcUrl))
                    .withDetail("reason", ex.getMessage())
                    .build();
        }
    }

    private String mask(String url) {
        return url.replaceAll("password=[^&]+", "password=***");
    }
}
