package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.Connector;
import com.pcitech.http.ingestion.core.domain.ConnectorVersion;
import com.pcitech.http.ingestion.core.dto.JiaduResultInfo;
import com.pcitech.http.ingestion.core.engine.TransformPipeline;
import com.pcitech.http.ingestion.core.metrics.IngestionMetrics;
import com.pcitech.http.ingestion.core.repository.ConnectorRepository;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import com.pcitech.http.ingestion.core.sink.RecordSinkDispatcher;
import com.pcitech.http.ingestion.core.webhook.EventInfoNormalizer;
import com.pcitech.http.ingestion.core.webhook.JiaduSignVerifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PushIngressService {

    private static final int MAX_ERROR_SAMPLES = 5;

    private final ConnectorRepository connectorRepository;
    private final JobRunRepository jobRunRepository;
    private final TransformPipeline transformPipeline;
    private final RecordSinkDispatcher recordSinkDispatcher;
    private final ObjectMapper objectMapper;
    private final IngestionMetrics ingestionMetrics;

    public PushIngressService(
            ConnectorRepository connectorRepository,
            JobRunRepository jobRunRepository,
            TransformPipeline transformPipeline,
            RecordSinkDispatcher recordSinkDispatcher,
            ObjectMapper objectMapper,
            IngestionMetrics ingestionMetrics
    ) {
        this.connectorRepository = connectorRepository;
        this.jobRunRepository = jobRunRepository;
        this.transformPipeline = transformPipeline;
        this.recordSinkDispatcher = recordSinkDispatcher;
        this.objectMapper = objectMapper;
        this.ingestionMetrics = ingestionMetrics;
    }

    public JiaduResultInfo ingest(
            String connectorId,
            String requestId,
            String signHeader,
            JsonNode body
    ) {
        Instant started = Instant.now();
        Connector connector = connectorRepository.findById(connectorId).orElse(null);
        if (connector == null) {
            return JiaduResultInfo.failure(1, "连接器不存在");
        }
        if (!isPushMode(connector.mode())) {
            return JiaduResultInfo.failure(1, "连接器模式不支持推送");
        }

        ConnectorVersion published = connectorRepository.findLatestPublished(connectorId).orElse(null);
        if (published == null) {
            return JiaduResultInfo.failure(1, "连接器未发布");
        }

        RuntimeConnectorConfig config;
        try {
            config = RuntimeConfigParser.parse(objectMapper.readTree(published.configJson()), objectMapper);
        } catch (Exception ex) {
            return JiaduResultInfo.failure(1, "连接器配置无效");
        }

        RuntimeConnectorConfig.WebhookSettings webhook = config.webhook() == null
                ? RuntimeConnectorConfig.WebhookSettings.defaults()
                : config.webhook();
        if (!webhook.enabled()) {
            return JiaduResultInfo.failure(1, "Webhook 未启用");
        }

        String eventId;
        try {
            JsonNode normalized = EventInfoNormalizer.normalize(objectMapper, body);
            eventId = normalized.path("EventID").asText(null);
            if (eventId == null || eventId.isBlank()) {
                return failJob(connectorId, published, "EventID 必填", started, 0, 0);
            }
            if (webhook.verifySign() && !JiaduSignVerifier.verify(webhook.platFlag(), eventId, signHeader)) {
                return failJob(connectorId, published, "sign 校验失败", started, 0, 0);
            }
            return processEvent(connectorId, published, config, normalized, requestId, started);
        } catch (IllegalArgumentException ex) {
            return failJob(connectorId, published, ex.getMessage(), started, 0, 0);
        } catch (Exception ex) {
            return failJob(connectorId, published, ex.getMessage(), started, 0, 0);
        }
    }

    private JiaduResultInfo processEvent(
            String connectorId,
            ConnectorVersion published,
            RuntimeConnectorConfig config,
            JsonNode normalized,
            String requestId,
            Instant started
    ) throws Exception {
        long jobRunId = jobRunRepository.create(connectorId, published.id(), "push");
        jobRunRepository.markRunning(jobRunId);
        int recordsOk = 0;
        int recordsFailed = 0;
        String finalStatus = "failed";
        try {
            if (config.sink() != null && !recordSinkDispatcher.isAvailable(config.sink())) {
                throw new IllegalStateException("Sink configured but destination is unavailable");
            }
            if (requestId != null && !requestId.isBlank()) {
                jobRunRepository.insertDetail(jobRunId, "ingress", null, null,
                        "x_request_id=" + requestId, null);
            }

            List<Object> sourceRecords = List.of(objectMapper.convertValue(normalized, Object.class));
            TransformPipeline.TransformResult transformResult = transformPipeline.transformTolerant(
                    sourceRecords,
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
            if (!transformed.isEmpty()) {
                Map<String, Object> row = transformed.get(0);
                row.put("raw_json", objectMapper.writeValueAsString(normalized));
            }

            if (config.sink() != null && !transformed.isEmpty()) {
                recordsOk = recordSinkDispatcher.write(transformed, config.sink());
            } else {
                recordsOk = transformed.size();
            }

            if (recordsFailed > 0) {
                throw new IllegalStateException("Transform failed for push event");
            }

            jobRunRepository.markSuccess(jobRunId, recordsOk, recordsFailed);
            finalStatus = "success";
            return JiaduResultInfo.success();
        } catch (Exception ex) {
            jobRunRepository.markFailed(jobRunId, recordsOk, recordsFailed, ex.getMessage());
            finalStatus = "failed";
            return JiaduResultInfo.failure(1, ex.getMessage() == null ? "处理失败" : ex.getMessage());
        } finally {
            ingestionMetrics.recordJobComplete(
                    connectorId,
                    "push",
                    finalStatus,
                    Duration.between(started, Instant.now()),
                    recordsOk,
                    recordsFailed
            );
        }
    }

    private JiaduResultInfo failJob(
            String connectorId,
            ConnectorVersion published,
            String message,
            Instant started,
            int recordsOk,
            int recordsFailed
    ) {
        if (published != null) {
            long jobRunId = jobRunRepository.create(connectorId, published.id(), "push");
            jobRunRepository.markRunning(jobRunId);
            jobRunRepository.markFailed(jobRunId, recordsOk, recordsFailed, message);
            ingestionMetrics.recordJobComplete(
                    connectorId,
                    "push",
                    "failed",
                    Duration.between(started, Instant.now()),
                    recordsOk,
                    recordsFailed
            );
        }
        return JiaduResultInfo.failure(1, message);
    }

    private static boolean isPushMode(String mode) {
        return "push".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }
}
