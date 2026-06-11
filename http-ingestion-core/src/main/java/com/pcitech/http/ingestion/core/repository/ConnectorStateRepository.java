package com.pcitech.http.ingestion.core.repository;

import com.pcitech.http.ingestion.core.domain.ConnectorState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ConnectorStateRepository {

    private final JdbcTemplate jdbc;

    public ConnectorStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ConnectorState> MAPPER = (rs, rowNum) -> new ConnectorState(
            rs.getString("connector_id"),
            rs.getString("watermark_json"),
            toInstant(rs.getTimestamp("updated_at"))
    );

    public Optional<ConnectorState> findByConnectorId(String connectorId) {
        List<ConnectorState> rows = jdbc.query(
                "SELECT * FROM connector_state WHERE connector_id = ?",
                MAPPER,
                connectorId
        );
        return rows.stream().findFirst();
    }

    public void upsertWatermark(String connectorId, String watermarkJson) {
        int updated = jdbc.update(
                "UPDATE connector_state SET watermark_json = ?, updated_at = CURRENT_TIMESTAMP WHERE connector_id = ?",
                watermarkJson, connectorId
        );
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO connector_state (connector_id, watermark_json) VALUES (?, ?)",
                    connectorId, watermarkJson
            );
        }
    }

    public void delete(String connectorId) {
        jdbc.update("DELETE FROM connector_state WHERE connector_id = ?", connectorId);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
