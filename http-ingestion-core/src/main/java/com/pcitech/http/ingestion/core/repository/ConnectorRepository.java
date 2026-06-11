package com.pcitech.http.ingestion.core.repository;

import com.pcitech.http.ingestion.core.domain.Connector;
import com.pcitech.http.ingestion.core.domain.ConnectorSummary;
import com.pcitech.http.ingestion.core.domain.ConnectorVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ConnectorRepository {

    private static final String SUMMARY_SQL =
            "SELECT c.id, c.name, c.mode, c.updated_at, "
                    + "MAX(CASE WHEN cv.status = 'published' THEN cv.version_number END) AS latest_published, "
                    + "SUM(CASE WHEN cv.status = 'draft' THEN 1 ELSE 0 END) > 0 AS has_draft "
                    + "FROM connector c "
                    + "LEFT JOIN connector_version cv ON cv.connector_id = c.id "
                    + "GROUP BY c.id, c.name, c.mode, c.updated_at "
                    + "ORDER BY c.updated_at DESC";

    private static final String PUBLISH_DRAFT_SQL =
            "UPDATE connector_version "
                    + "SET status = 'published', version_number = ?, published_at = CURRENT_TIMESTAMP "
                    + "WHERE connector_id = ? AND status = 'draft'";

    private static final String LATEST_PUBLISHED_SQL =
            "SELECT * FROM connector_version "
                    + "WHERE connector_id = ? AND status = 'published' "
                    + "ORDER BY version_number DESC "
                    + "LIMIT 1";

    private final JdbcTemplate jdbc;

    public ConnectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Connector> CONNECTOR_MAPPER = (rs, rowNum) -> new Connector(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("mode"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private static final RowMapper<ConnectorVersion> VERSION_MAPPER = (rs, rowNum) -> new ConnectorVersion(
            rs.getLong("id"),
            rs.getString("connector_id"),
            (Integer) rs.getObject("version_number"),
            rs.getString("status"),
            rs.getString("config_json"),
            toInstant(rs.getTimestamp("created_at")),
            rs.getTimestamp("published_at") == null ? null : toInstant(rs.getTimestamp("published_at"))
    );

    public List<ConnectorSummary> findAllSummaries() {
        return jdbc.query(SUMMARY_SQL, (rs, rowNum) -> new ConnectorSummary(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("mode"),
                rs.getBoolean("has_draft"),
                (Integer) rs.getObject("latest_published"),
                toInstant(rs.getTimestamp("updated_at"))
        ));
    }

    public Optional<Connector> findById(String id) {
        List<Connector> rows = jdbc.query(
                "SELECT * FROM connector WHERE id = ?",
                CONNECTOR_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public boolean existsById(String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM connector WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    public void insertConnector(String id, String name, String mode) {
        jdbc.update(
                "INSERT INTO connector (id, name, mode) VALUES (?, ?, ?)",
                id, name, mode
        );
    }

    public void updateConnector(String id, String name, String mode) {
        jdbc.update(
                "UPDATE connector SET name = ?, mode = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                name, mode, id
        );
    }

    public void deleteConnector(String id) {
        jdbc.update("DELETE FROM connector WHERE id = ?", id);
    }

    public void insertDraftVersion(String connectorId, String configJson) {
        jdbc.update(
                "INSERT INTO connector_version (connector_id, status, config_json) VALUES (?, 'draft', ?)",
                connectorId, configJson
        );
    }

    public Optional<ConnectorVersion> findDraftVersion(String connectorId) {
        List<ConnectorVersion> rows = jdbc.query(
                "SELECT * FROM connector_version WHERE connector_id = ? AND status = 'draft'",
                VERSION_MAPPER,
                connectorId
        );
        return rows.stream().findFirst();
    }

    public void updateDraftConfig(String connectorId, String configJson) {
        int updated = jdbc.update(
                "UPDATE connector_version SET config_json = ? WHERE connector_id = ? AND status = 'draft'",
                configJson, connectorId
        );
        if (updated == 0) {
            insertDraftVersion(connectorId, configJson);
        }
    }

    public int nextPublishedVersionNumber(String connectorId) {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(version_number) FROM connector_version WHERE connector_id = ? AND status = 'published'",
                Integer.class,
                connectorId
        );
        return max == null ? 1 : max + 1;
    }

    public void publishDraft(String connectorId, int versionNumber) {
        jdbc.update(PUBLISH_DRAFT_SQL, versionNumber, connectorId);
        jdbc.update(
                "UPDATE connector SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                connectorId
        );
    }

    public void insertDraftFromPublished(String connectorId, String configJson) {
        jdbc.update(
                "INSERT INTO connector_version (connector_id, status, config_json) VALUES (?, 'draft', ?)",
                connectorId, configJson
        );
    }

    public Optional<ConnectorVersion> findLatestPublished(String connectorId) {
        List<ConnectorVersion> rows = jdbc.query(LATEST_PUBLISHED_SQL, VERSION_MAPPER, connectorId);
        return rows.stream().findFirst();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
