package com.pcitech.http.ingestion.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigParserCursorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseCursorPagination_queryWithStopWhen() throws Exception {
        String json = """
                {
                  "pagination": {
                    "strategy": "cursor",
                    "location": "query",
                    "cursor_param": "pageToken",
                    "cursor_response_path": "$.meta.next",
                    "has_more_path": "$.meta.hasMore",
                    "stop_when": ["empty_cursor", "has_more_false", "empty_page"],
                    "page_size": 50
                  }
                }
                """;
        RuntimeConnectorConfig config = RuntimeConfigParser.parse(objectMapper.readTree(json), objectMapper);
        var pagination = config.pagination();
        assertThat(pagination.strategy()).isEqualTo("cursor");
        assertThat(pagination.cursorParam()).isEqualTo("pageToken");
        assertThat(pagination.cursorResponsePath()).isEqualTo("$.meta.next");
        assertThat(pagination.hasMorePath()).isEqualTo("$.meta.hasMore");
        assertThat(pagination.stopWhen()).containsExactly("empty_cursor", "has_more_false", "empty_page");
        assertThat(pagination.pageSize()).isEqualTo(50);
    }
}
