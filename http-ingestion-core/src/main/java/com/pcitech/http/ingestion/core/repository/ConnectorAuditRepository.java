package com.pcitech.http.ingestion.core.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConnectorAuditRepository {

    private final JdbcTemplate jdbc;

    public ConnectorAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String connectorId, String action, String detail) {
        jdbc.update(
                "INSERT INTO connector_audit (connector_id, action, detail) VALUES (?, ?, ?)",
                connectorId,
                action,
                truncate(detail)
        );
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
