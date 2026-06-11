package com.pcitech.http.ingestion.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyComposerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void compose_dahuaBodyPaginationAndIncremental() throws Exception {
        RuntimeConnectorConfig.PaginationSettings pagination = new RuntimeConnectorConfig.PaginationSettings(
                "page_page_size", "body", "page_number", "page", "pageSize", 1, 2, "$.totalCount", 10,
                "json_path", null, null, true
        );
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "$.capTime", null, "body", "condition.startTime", "condition.endTime", "dahua_utc", "5m"
        );
        String template = """
                {"page":1,"pageSize":100,"condition":{}}
                """;
        Instant watermark = Instant.parse("2025-06-01T08:30:00Z");

        String body = RequestBodyComposer.compose(
                objectMapper, template, pagination, incremental, 2, watermark, true
        );

        var root = objectMapper.readTree(body);
        assertThat(root.path("page").asInt()).isEqualTo(2);
        assertThat(root.path("pageSize").asInt()).isEqualTo(2);
        assertThat(root.path("condition").path("startTime").asText()).isEqualTo("20250601T082500Z");
        assertThat(root.path("condition").path("endTime").asText()).isNotBlank();
    }

    @Test
    void compose_offsetLimitOffsetAndLimitInBody() throws Exception {
        RuntimeConnectorConfig.PaginationSettings pagination = new RuntimeConnectorConfig.PaginationSettings(
                "offset_limit", "body", "page_number", "offset", "limit", 0, 2, null, 10,
                "none", null, null, true
        );
        String template = """
                {"offset":0,"limit":100}
                """;

        String pageZero = RequestBodyComposer.compose(
                objectMapper, template, pagination, RuntimeConnectorConfig.IncrementalSettings.disabled(), 0, null, false
        );
        String pageOne = RequestBodyComposer.compose(
                objectMapper, template, pagination, RuntimeConnectorConfig.IncrementalSettings.disabled(), 1, null, false
        );

        var first = objectMapper.readTree(pageZero);
        var second = objectMapper.readTree(pageOne);
        assertThat(first.path("offset").asInt()).isZero();
        assertThat(first.path("limit").asInt()).isEqualTo(2);
        assertThat(second.path("offset").asInt()).isEqualTo(2);
        assertThat(second.path("limit").asInt()).isEqualTo(2);
    }

    @Test
    void compose_dahuaQueryPaginationAndBodyIncremental() throws Exception {
        RuntimeConnectorConfig.PaginationSettings pagination = new RuntimeConnectorConfig.PaginationSettings(
                "page_page_size", "query", "page_number", "page", "pageSize", 1, 2, "$.totalCount", 10,
                "json_path", null, null, true
        );
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "$.capTime", null, "body", "startTimeStrUtc", "endTimeStrUtc", "dahua_utc", "5m"
        );
        String template = "{}";
        Instant watermark = Instant.parse("2025-06-01T08:30:00Z");

        String body = RequestBodyComposer.compose(
                objectMapper, template, pagination, incremental, 1, watermark, true
        );

        var root = objectMapper.readTree(body);
        assertThat(root.path("startTimeStrUtc").asText()).isEqualTo("20250601T082500Z");
        assertThat(root.path("endTimeStrUtc").asText()).isNotBlank();
    }

    @Test
    void compose_meiyaSkipLimitAndEvccRange() throws Exception {
        RuntimeConnectorConfig.PaginationSettings pagination = new RuntimeConnectorConfig.PaginationSettings(
                "page_page_size", "body", "skip_limit", "page.skip", "page.limit", 1, 50, "$.states.total", 10,
                "json_path", null, null, true
        );
        RuntimeConnectorConfig.IncrementalSettings incremental = new RuntimeConnectorConfig.IncrementalSettings(
                true, "$.evcc", null, "body", "params.evcc", null, "meiya_datetime", "5m"
        );
        String template = """
                {"params":{},"page":{"skip":0,"limit":100}}
                """;
        Instant watermark = Instant.parse("2025-06-01T08:30:00Z");

        String body = RequestBodyComposer.compose(
                objectMapper, template, pagination, incremental, 2, watermark, true
        );

        var root = objectMapper.readTree(body);
        assertThat(root.path("page").path("skip").asLong()).isEqualTo(50);
        assertThat(root.path("page").path("limit").asLong()).isEqualTo(50);
        assertThat(root.path("params").path("evcc").isArray()).isTrue();
        assertThat(root.path("params").path("evcc").get(0).asText()).isEqualTo("2025-06-01 08:25:00");
    }
}
