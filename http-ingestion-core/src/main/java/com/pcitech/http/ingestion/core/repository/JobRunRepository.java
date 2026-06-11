package com.pcitech.http.ingestion.core.repository;

import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.domain.JobRunDetail;
import com.pcitech.http.ingestion.core.dto.ConnectorJobStatsDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JobRunRepository {

    private final JdbcTemplate jdbc;

    public JobRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<JobRunDetail> DETAIL_MAPPER = (rs, rowNum) -> new JobRunDetail(
            rs.getLong("id"),
            rs.getLong("job_run_id"),
            rs.getString("stage"),
            (Integer) rs.getObject("page_number"),
            (Integer) rs.getObject("record_index"),
            rs.getString("message"),
            rs.getString("sample_json")
    );

    private static final RowMapper<JobRun> MAPPER = (rs, rowNum) -> new JobRun(
            rs.getLong("id"),
            rs.getString("connector_id"),
            (Long) rs.getObject("connector_version_id"),
            rs.getString("run_type"),
            rs.getString("status"),
            toInstant(rs.getTimestamp("started_at")),
            rs.getTimestamp("finished_at") == null ? null : toInstant(rs.getTimestamp("finished_at")),
            rs.getInt("records_ok"),
            rs.getInt("records_failed"),
            rs.getString("error_message")
    );

    public long create(String connectorId, Long connectorVersionId, String runType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO job_run (connector_id, connector_version_id, run_type, status) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, connectorId);
            if (connectorVersionId == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, connectorVersionId);
            }
            ps.setString(3, runType);
            ps.setString(4, JobRun.STATUS_PENDING);
            return ps;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && keys.get("ID") != null) {
            return ((Number) keys.get("ID")).longValue();
        }
        if (keys != null && keys.get("id") != null) {
            return ((Number) keys.get("id")).longValue();
        }
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public void markRunning(long jobRunId) {
        jdbc.update("UPDATE job_run SET status = ?, started_at = CURRENT_TIMESTAMP WHERE id = ?",
                JobRun.STATUS_RUNNING, jobRunId);
    }

    public void markSuccess(long jobRunId, int recordsOk, int recordsFailed) {
        jdbc.update(
                "UPDATE job_run SET status = ?, finished_at = CURRENT_TIMESTAMP, records_ok = ?, records_failed = ? WHERE id = ?",
                JobRun.STATUS_SUCCESS, recordsOk, recordsFailed, jobRunId
        );
    }

    public void markFailed(long jobRunId, int recordsOk, int recordsFailed, String errorMessage) {
        jdbc.update(
                "UPDATE job_run SET status = ?, finished_at = CURRENT_TIMESTAMP, records_ok = ?, records_failed = ?, error_message = ? WHERE id = ?",
                JobRun.STATUS_FAILED, recordsOk, recordsFailed, truncate(errorMessage), jobRunId
        );
    }

    public void failStalePendingRuns(String connectorId) {
        jdbc.update(
                "UPDATE job_run SET status = ?, finished_at = CURRENT_TIMESTAMP, error_message = ? "
                        + "WHERE connector_id = ? AND status = ?",
                JobRun.STATUS_FAILED, "stale pending run cleared", connectorId, JobRun.STATUS_PENDING
        );
    }

    public boolean hasActiveRun(String connectorId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_run WHERE connector_id = ? AND status IN ('pending', 'running')",
                Integer.class,
                connectorId
        );
        return count != null && count > 0;
    }

    public Optional<JobRun> findById(long id) {
        List<JobRun> rows = jdbc.query("SELECT * FROM job_run WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public List<JobRun> findByConnectorId(String connectorId, int limit) {
        return jdbc.query(
                "SELECT * FROM job_run WHERE connector_id = ? ORDER BY started_at DESC LIMIT ?",
                MAPPER,
                connectorId,
                limit
        );
    }

    public List<JobRunDetail> findDetailsByJobRunId(long jobRunId, int limit) {
        return jdbc.query(
                "SELECT * FROM job_run_detail WHERE job_run_id = ? ORDER BY id ASC LIMIT ?",
                DETAIL_MAPPER,
                jobRunId,
                limit
        );
    }

    public List<JobRun> findRecent(int limit) {
        return jdbc.query(
                "SELECT * FROM job_run ORDER BY started_at DESC LIMIT ?",
                MAPPER,
                limit
        );
    }

    public List<ConnectorJobStatsDto> aggregateByConnector() {
        return jdbc.query(
                """
                        SELECT connector_id,
                               COUNT(*) AS total_jobs,
                               SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_jobs,
                               SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_jobs,
                               COALESCE(SUM(records_ok), 0) AS records_ok,
                               COALESCE(SUM(records_failed), 0) AS records_failed
                        FROM job_run
                        GROUP BY connector_id
                        ORDER BY connector_id
                        """,
                (rs, rowNum) -> new ConnectorJobStatsDto(
                        rs.getString("connector_id"),
                        rs.getLong("total_jobs"),
                        rs.getLong("success_jobs"),
                        rs.getLong("failed_jobs"),
                        rs.getLong("records_ok"),
                        rs.getLong("records_failed")
                )
        );
    }

    public GlobalJobStats aggregateGlobal() {
        List<GlobalJobStats> rows = jdbc.query(
                """
                        SELECT COUNT(*) AS total_jobs,
                               SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_jobs,
                               SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_jobs,
                               COALESCE(SUM(records_ok), 0) AS records_ok,
                               COALESCE(SUM(records_failed), 0) AS records_failed
                        FROM job_run
                        """,
                (rs, rowNum) -> new GlobalJobStats(
                        rs.getLong("total_jobs"),
                        rs.getLong("success_jobs"),
                        rs.getLong("failed_jobs"),
                        rs.getLong("records_ok"),
                        rs.getLong("records_failed")
                )
        );
        if (rows.isEmpty()) {
            return new GlobalJobStats(0, 0, 0, 0, 0);
        }
        return rows.getFirst();
    }

    public record GlobalJobStats(
            long totalJobs,
            long successJobs,
            long failedJobs,
            long recordsOk,
            long recordsFailed
    ) {
    }

    public void insertDetail(long jobRunId, String stage, Integer pageNumber, Integer recordIndex, String message, String sampleJson) {
        jdbc.update(
                "INSERT INTO job_run_detail (job_run_id, stage, page_number, record_index, message, sample_json) VALUES (?, ?, ?, ?, ?, ?)",
                jobRunId, stage, pageNumber, recordIndex, truncate(message), sampleJson
        );
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
