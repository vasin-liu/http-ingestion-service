package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.ConnectorState;
import com.pcitech.http.ingestion.core.domain.ConnectorVersion;
import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.domain.WatermarkState;
import com.pcitech.http.ingestion.core.engine.HttpPullEngine;
import com.pcitech.http.ingestion.core.engine.TransformPipeline;
import com.pcitech.http.ingestion.core.metrics.IngestionMetrics;
import com.pcitech.http.ingestion.core.repository.ConnectorAuditRepository;
import com.pcitech.http.ingestion.core.repository.ConnectorRepository;
import com.pcitech.http.ingestion.core.repository.ConnectorStateRepository;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import com.pcitech.http.ingestion.core.sink.RecordSinkDispatcher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SyncService {

    public enum SyncType {
        FULL, INCREMENTAL, SAMPLE
    }

    public record SyncOptions(SyncType type, Integer recordLimit, boolean writeSink) {
        public static SyncOptions full() {
            return new SyncOptions(SyncType.FULL, null, true);
        }

        public static SyncOptions incremental() {
            return new SyncOptions(SyncType.INCREMENTAL, null, true);
        }

        public static SyncOptions sample(int limit, boolean writeSink) {
            return new SyncOptions(SyncType.SAMPLE, limit, writeSink);
        }

        public String runType() {
            return type == SyncType.SAMPLE ? "sample" : type.name().toLowerCase();
        }
    }

    private static final int MAX_ERROR_SAMPLES = 20;

    private final ConnectorRepository connectorRepository;
    private final ConnectorStateRepository stateRepository;
    private final ConnectorAuditRepository auditRepository;
    private final JobRunRepository jobRunRepository;
    private final HttpPullEngine pullEngine;
    private final TransformPipeline transformPipeline;
    private final RecordSinkDispatcher recordSinkDispatcher;
    private final ObjectMapper objectMapper;
    private final IngestionMetrics ingestionMetrics;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SyncService(
            ConnectorRepository connectorRepository,
            ConnectorStateRepository stateRepository,
            ConnectorAuditRepository auditRepository,
            JobRunRepository jobRunRepository,
            HttpPullEngine pullEngine,
            TransformPipeline transformPipeline,
            RecordSinkDispatcher recordSinkDispatcher,
            ObjectMapper objectMapper,
            IngestionMetrics ingestionMetrics
    ) {
        this.connectorRepository = connectorRepository;
        this.stateRepository = stateRepository;
        this.auditRepository = auditRepository;
        this.jobRunRepository = jobRunRepository;
        this.pullEngine = pullEngine;
        this.transformPipeline = transformPipeline;
        this.recordSinkDispatcher = recordSinkDispatcher;
        this.objectMapper = objectMapper;
        this.ingestionMetrics = ingestionMetrics;
    }

    public long triggerAsync(String connectorId, SyncOptions options) {
        if (jobRunRepository.hasActiveRun(connectorId)) {
            jobRunRepository.failStalePendingRuns(connectorId);
            if (jobRunRepository.hasActiveRun(connectorId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Connector sync already running");
            }
        }
        ConnectorVersion published = connectorRepository.findLatestPublished(connectorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No published version to sync"));
        long jobRunId = jobRunRepository.create(connectorId, published.id(), options.runType());
        executor.submit(() -> execute(jobRunId, connectorId, published, options));
        return jobRunId;
    }

    public void execute(long jobRunId, String connectorId, ConnectorVersion published, SyncOptions options) {
        Instant started = Instant.now();
        jobRunRepository.markRunning(jobRunId);
        int recordsOk = 0;
        int recordsFailed = 0;
        String finalStatus = JobRun.STATUS_FAILED;
        try {
            JsonNode configNode = objectMapper.readTree(published.configJson());
            RuntimeConnectorConfig config = RuntimeConfigParser.parse(configNode, objectMapper);
            validateConfig(config, options);

            WatermarkState watermark = readWatermarkState(connectorId);
            boolean incrementalMode = resolveIncrementalMode(options, config, watermark);
            Integer maxRecords = options.type() == SyncType.SAMPLE ? options.recordLimit() : null;

            HttpPullEngine.PullResult pullResult = pullEngine.pull(
                    config,
                    watermark,
                    incrementalMode,
                    maxRecords,
                    (page, count, duration) -> jobRunRepository.insertDetail(
                            jobRunId, "http", page, null,
                            "page fetched records=" + count + " durationMs=" + duration,
                            null
                    )
            );

            TransformPipeline.TransformResult transformResult = transformPipeline.transformTolerant(
                    pullResult.records(),
                    config.transform(),
                    MAX_ERROR_SAMPLES
            );
            for (TransformPipeline.TransformError error : transformResult.errors()) {
                recordsFailed++;
                jobRunRepository.insertDetail(
                        jobRunId,
                        "transform",
                        null,
                        error.recordIndex(),
                        error.message(),
                        error.sampleJson()
                );
            }

            List<Map<String, Object>> transformed = transformResult.records();
            if (options.writeSink() && config.sink() != null) {
                recordsOk = recordSinkDispatcher.write(transformed, config.sink());
            } else {
                recordsOk = transformed.size();
            }

            if (options.type() != SyncType.SAMPLE
                    && config.incremental().enabled()
                    && hasPersistableWatermark(pullResult.watermarkState(), config.incremental())) {
                ObjectNode wm = objectMapper.createObjectNode();
                WatermarkState state = pullResult.watermarkState();
                if (state.hasTimestamp()) {
                    wm.put("timestamp", state.timestamp().toString());
                }
                if (state.hasLastId()) {
                    wm.put("last_id", state.lastId());
                }
                stateRepository.upsertWatermark(connectorId, objectMapper.writeValueAsString(wm));
                if (!config.incremental().isMonotonicId() && state.hasTimestamp()) {
                    ingestionMetrics.updateWatermarkLag(connectorId, state.timestamp());
                }
            }

            jobRunRepository.markSuccess(jobRunId, recordsOk, recordsFailed);
            finalStatus = JobRun.STATUS_SUCCESS;
        } catch (HttpPullEngine.PullException ex) {
            jobRunRepository.insertDetail(jobRunId, "http", ex.pageNumber(), null, ex.getMessage(), null);
            jobRunRepository.markFailed(jobRunId, recordsOk, recordsFailed, ex.getMessage());
        } catch (Exception ex) {
            jobRunRepository.markFailed(jobRunId, recordsOk, recordsFailed, ex.getMessage());
        } finally {
            ingestionMetrics.recordJobComplete(
                    connectorId,
                    options.runType(),
                    finalStatus,
                    Duration.between(started, Instant.now()),
                    recordsOk,
                    recordsFailed
            );
        }
    }

    public Optional<ConnectorState> getState(String connectorId) {
        connectorRepository.findById(connectorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));
        Optional<ConnectorState> state = stateRepository.findByConnectorId(connectorId);
        state.ifPresent(s -> ingestionMetrics.updateWatermarkLag(connectorId, parseWatermark(s.watermarkJson())));
        return state;
    }

    public void resetState(String connectorId) {
        connectorRepository.findById(connectorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));
        stateRepository.delete(connectorId);
        auditRepository.insert(connectorId, "state_reset", "watermark cleared by operator");
        ingestionMetrics.updateWatermarkLag(connectorId, null);
    }

    private void validateConfig(RuntimeConnectorConfig config, SyncOptions options) {
        if (config.http() == null || config.http().url() == null) {
            throw new IllegalArgumentException("HTTP url is required in published config");
        }
        boolean needsSink = options.writeSink() && config.sink() != null;
        if (needsSink && !recordSinkDispatcher.isAvailable(config.sink())) {
            throw new IllegalStateException("Sink configured but destination is unavailable");
        }
        if (options.type() == SyncType.SAMPLE && (options.recordLimit() == null || options.recordLimit() <= 0)) {
            throw new IllegalArgumentException("Sample sync requires positive limit");
        }
    }

    private WatermarkState readWatermarkState(String connectorId) {
        return stateRepository.findByConnectorId(connectorId)
                .map(ConnectorState::watermarkJson)
                .map(this::parseWatermarkState)
                .orElse(WatermarkState.empty());
    }

    private WatermarkState parseWatermarkState(String json) {
        if (json == null) {
            return WatermarkState.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Instant timestamp = node.hasNonNull("timestamp")
                    ? Instant.parse(node.get("timestamp").asText())
                    : null;
            String lastId = node.hasNonNull("last_id") ? node.get("last_id").asText() : null;
            return new WatermarkState(timestamp, lastId);
        } catch (Exception ignored) {
        }
        return WatermarkState.empty();
    }

    private Instant parseWatermark(String json) {
        return parseWatermarkState(json).timestamp();
    }

    private boolean hasPersistableWatermark(
            WatermarkState watermark,
            RuntimeConnectorConfig.IncrementalSettings incremental
    ) {
        if (watermark == null) {
            return false;
        }
        if (incremental.isMonotonicId()) {
            return watermark.hasLastId();
        }
        return watermark.hasTimestamp();
    }

    private boolean resolveIncrementalMode(SyncOptions options, RuntimeConnectorConfig config, WatermarkState watermark) {
        if (options.type() == SyncType.FULL || options.type() == SyncType.SAMPLE) {
            return false;
        }
        if (!config.incremental().enabled()) {
            return false;
        }
        if (config.incremental().isMonotonicId()) {
            if (!watermark.hasLastId()) {
                return !"full".equalsIgnoreCase(config.sync().onFirstRun());
            }
            return true;
        }
        if (!watermark.hasTimestamp()) {
            return !"full".equalsIgnoreCase(config.sync().onFirstRun());
        }
        return true;
    }
}
