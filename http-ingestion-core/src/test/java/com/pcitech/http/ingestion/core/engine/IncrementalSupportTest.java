package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.WatermarkState;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncrementalSupportTest {

    private final JsonPathSupport jsonPathSupport = new JsonPathSupport(new ObjectMapper());

    @Test
    void applyQuery_monotonicId_putsSinceId() {
        Map<String, String> query = new HashMap<>();
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "monotonic_id", "$.id", "since_id", "query", null, null, null, null, null
        );

        IncrementalSupport.applyQuery(
                query,
                incremental,
                new WatermarkState(null, "42"),
                true
        );

        assertThat(query).containsEntry("since_id", "42");
    }

    @Test
    void applyQuery_timestamp_appliesOverlap() {
        Map<String, String> query = new HashMap<>();
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "timestamp", "$.updated_at", "updated_after", "query", null, null, "iso_instant", "5m", null
        );
        Instant watermark = Instant.parse("2025-06-01T08:30:00Z");

        IncrementalSupport.applyQuery(
                query,
                incremental,
                new WatermarkState(watermark, null),
                true
        );

        assertThat(query).containsEntry("updated_after", "2025-06-01T08:25:00Z");
    }

    @Test
    void advance_monotonicId_tracksMaxId() {
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "monotonic_id", "$.id", "since_id", "query", null, null, null, null, null
        );
        List<Object> records = List.of(
                Map.of("id", 1, "name", "A"),
                Map.of("id", 5, "name", "E"),
                Map.of("id", 3, "name", "C")
        );

        WatermarkState advanced = IncrementalSupport.advance(
                WatermarkState.empty(),
                records,
                incremental,
                jsonPathSupport
        );

        assertThat(advanced.lastId()).isEqualTo("5");
    }

    @Test
    void bumpTimestampIfUnchanged_skipsMonotonicMode() {
        WatermarkState previous = new WatermarkState(Instant.parse("2025-06-01T08:00:00Z"), "10");
        WatermarkState advanced = new WatermarkState(Instant.parse("2025-06-01T08:00:00Z"), "12");
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "monotonic_id", "$.id", "since_id", "query", null, null, null, null, null
        );

        WatermarkState result = IncrementalSupport.bumpTimestampIfUnchanged(
                advanced, previous, incremental, true
        );

        assertThat(result).isEqualTo(advanced);
    }

    @Test
    void applyQuery_rollingWindow_putsStartAndEnd() {
        Map<String, String> query = new HashMap<>();
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "rolling_window", "$.updated_at", "startTime", "query",
                null, null, "iso_instant", "5m", "endTime"
        );
        Instant watermark = Instant.parse("2025-06-01T10:00:00Z");

        IncrementalSupport.applyQuery(
                query,
                incremental,
                new WatermarkState(watermark, null),
                true
        );

        assertThat(query).containsKey("startTime");
        assertThat(query).containsKey("endTime");
        assertThat(query.get("startTime")).isEqualTo("2025-06-01T09:55:00Z");
    }

    @Test
    void advance_rollingWindow_fullSyncTracksMaxTimestamp() {
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "rolling_window", "$.updated_at", "startTime", "query",
                null, null, "iso_instant", "5m", "endTime"
        );
        List<Object> records = List.of(
                Map.of("id", 1, "updated_at", "2025-06-01T08:00:00Z"),
                Map.of("id", 2, "updated_at", "2025-06-01T10:00:00Z")
        );

        WatermarkState advanced = IncrementalSupport.advance(
                WatermarkState.empty(),
                records,
                incremental,
                jsonPathSupport,
                false
        );

        assertThat(advanced.timestamp()).isEqualTo(Instant.parse("2025-06-01T10:00:00Z"));
    }

    @Test
    void bumpTimestampIfUnchanged_rollingWindow_advancesWindowEnd() {
        WatermarkState previous = new WatermarkState(Instant.parse("2025-06-01T08:00:00Z"), null);
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "rolling_window", "$.updated_at", "startTime", "query",
                null, null, "iso_instant", "5m", "endTime"
        );

        WatermarkState result = IncrementalSupport.bumpTimestampIfUnchanged(
                previous, previous, incremental, true
        );

        assertThat(result.timestamp()).isAfter(previous.timestamp());
    }
}
