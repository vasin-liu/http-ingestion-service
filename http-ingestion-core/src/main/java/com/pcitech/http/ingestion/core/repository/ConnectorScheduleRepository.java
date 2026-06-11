package com.pcitech.http.ingestion.core.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ConnectorScheduleRepository {

    private final JdbcTemplate jdbc;

    public ConnectorScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String connectorId, String scheduleType, String expression, boolean enabled) {
        int updated = jdbc.update(
                "UPDATE connector_schedule SET schedule_type = ?, expression = ?, enabled = ? WHERE connector_id = ?",
                scheduleType, expression, enabled, connectorId
        );
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO connector_schedule (connector_id, schedule_type, expression, enabled) VALUES (?, ?, ?, ?)",
                    connectorId, scheduleType, expression, enabled
            );
        }
    }

    public void disable(String connectorId) {
        jdbc.update("UPDATE connector_schedule SET enabled = FALSE WHERE connector_id = ?", connectorId);
    }

    public void setEnabled(String connectorId, boolean enabled) {
        jdbc.update("UPDATE connector_schedule SET enabled = ? WHERE connector_id = ?", enabled, connectorId);
    }

    public Optional<ConnectorScheduleRow> findByConnectorId(String connectorId) {
        return jdbc.query(
                "SELECT schedule_type, expression, enabled FROM connector_schedule WHERE connector_id = ?",
                rs -> rs.next()
                        ? Optional.of(new ConnectorScheduleRow(
                                rs.getString("schedule_type"),
                                rs.getString("expression"),
                                rs.getBoolean("enabled")))
                        : Optional.empty(),
                connectorId
        );
    }

    public boolean isEnabled(String connectorId) {
        Boolean enabled = jdbc.query(
                "SELECT enabled FROM connector_schedule WHERE connector_id = ?",
                rs -> rs.next() ? rs.getBoolean("enabled") : null,
                connectorId
        );
        return Boolean.TRUE.equals(enabled);
    }
}
