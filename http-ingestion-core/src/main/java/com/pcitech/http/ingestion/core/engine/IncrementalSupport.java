package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.WatermarkState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class IncrementalSupport {

    private static final Duration DEFAULT_END_OFFSET = Duration.ofMinutes(1);

    private IncrementalSupport() {
    }

    record WindowBounds(Instant start, Instant end) {
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
        if (incremental.isRollingWindow()) {
            WindowBounds window = resolveWindow(watermark, incremental);
            if (incremental.requestParam() != null && !incremental.requestParam().isBlank()) {
                query.put(
                        incremental.requestParam(),
                        RequestBodyComposer.formatTime(window.start(), incremental.timeFormat())
                );
            }
            if (incremental.requestEndParam() != null && !incremental.requestEndParam().isBlank()) {
                query.put(
                        incremental.requestEndParam(),
                        RequestBodyComposer.formatTime(window.end(), incremental.timeFormat())
                );
            }
            return;
        }
        if (watermark.hasTimestamp() && incremental.requestParam() != null) {
            Instant effective = watermark.timestamp().minus(parseOverlap(incremental.overlap()));
            query.put(incremental.requestParam(), RequestBodyComposer.formatTime(effective, incremental.timeFormat()));
        }
    }

    static WindowBounds resolveWindow(WatermarkState watermark, RuntimeConnectorConfig.IncrementalSettings incremental) {
        Instant end = Instant.now().plus(DEFAULT_END_OFFSET);
        Duration overlap = parseOverlap(incremental.overlap());
        Instant start = watermark.hasTimestamp() ? watermark.timestamp().minus(overlap) : end.minus(overlap);
        return new WindowBounds(start, end);
    }

    static WatermarkState advance(
            WatermarkState current,
            List<Object> records,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            JsonPathSupport jsonPathSupport,
            boolean incrementalMode
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
        if (incremental.isRollingWindow()) {
            if (!incrementalMode) {
                return advanceTimestamp(current, records, incremental, jsonPathSupport);
            }
            return current;
        }
        return advanceTimestamp(current, records, incremental, jsonPathSupport);
    }

    static WatermarkState advance(
            WatermarkState current,
            List<Object> records,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            JsonPathSupport jsonPathSupport
    ) {
        return advance(current, records, incremental, jsonPathSupport, true);
    }

    private static WatermarkState advanceTimestamp(
            WatermarkState current,
            List<Object> records,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            JsonPathSupport jsonPathSupport
    ) {
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
        if (incremental.isRollingWindow()) {
            WindowBounds window = resolveWindow(advanced, incremental);
            return new WatermarkState(window.end(), advanced.lastId());
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

    static Duration parseOverlap(String overlap) {
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
