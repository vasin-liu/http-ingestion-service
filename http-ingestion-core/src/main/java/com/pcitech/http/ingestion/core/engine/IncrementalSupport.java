package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.WatermarkState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class IncrementalSupport {

    private IncrementalSupport() {
    }

    static void applyQuery(
            Map<String, String> query,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            WatermarkState watermark,
            boolean incrementalMode
    ) {
        if (!incrementalMode || incremental == null || !incremental.enabled()) {
            return;
        }
        if (!"query".equalsIgnoreCase(incremental.requestTarget())) {
            return;
        }
        if (incremental.isMonotonicId()) {
            if (watermark.hasLastId() && incremental.requestParam() != null) {
                query.put(incremental.requestParam(), watermark.lastId());
            }
            return;
        }
        if (watermark.hasTimestamp() && incremental.requestParam() != null) {
            Instant effective = watermark.timestamp().minus(parseOverlap(incremental.overlap()));
            query.put(incremental.requestParam(), RequestBodyComposer.formatTime(effective, incremental.timeFormat()));
        }
    }

    static WatermarkState advance(
            WatermarkState current,
            List<Object> records,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            JsonPathSupport jsonPathSupport
    ) {
        if (incremental == null || !incremental.enabled() || records.isEmpty()) {
            return current;
        }
        if (incremental.isMonotonicId()) {
            String maxId = current.lastId();
            for (Object record : records) {
                String id = jsonPathSupport.readMonotonicId(record, incremental.responsePath());
                if (id != null && compareMonotonicId(id, maxId) > 0) {
                    maxId = id;
                }
            }
            return new WatermarkState(current.timestamp(), maxId);
        }
        Instant maxTimestamp = current.timestamp();
        for (Object record : records) {
            Instant ts = jsonPathSupport.readInstant(record, incremental.responsePath());
            if (ts != null && (maxTimestamp == null || ts.isAfter(maxTimestamp))) {
                maxTimestamp = ts;
            }
        }
        return new WatermarkState(maxTimestamp, current.lastId());
    }

    static WatermarkState bumpTimestampIfUnchanged(
            WatermarkState advanced,
            WatermarkState previous,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            boolean incrementalMode
    ) {
        if (!incrementalMode || incremental == null || !incremental.enabled() || incremental.isMonotonicId()) {
            return advanced;
        }
        if (advanced.hasTimestamp()
                && (previous == null || !previous.hasTimestamp() || !advanced.timestamp().isAfter(previous.timestamp()))) {
            return new WatermarkState(Instant.now(), advanced.lastId());
        }
        return advanced;
    }

    static int compareMonotonicId(String candidate, String currentMax) {
        if (currentMax == null || currentMax.isBlank()) {
            return 1;
        }
        try {
            long candidateValue = Long.parseLong(candidate);
            long currentValue = Long.parseLong(currentMax);
            return Long.compare(candidateValue, currentValue);
        } catch (NumberFormatException ex) {
            return candidate.compareTo(currentMax);
        }
    }

    private static Duration parseOverlap(String overlap) {
        if (overlap == null || overlap.isBlank()) {
            return Duration.ZERO;
        }
        String value = overlap.trim().toLowerCase();
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return Duration.ofMinutes(5);
    }
}
