package com.pcitech.http.ingestion.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigParserRollingWindowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parse_rollingWindowMode() throws Exception {
        var config = RuntimeConfigParser.parse(objectMapper.readTree("""
                {
                  "http": { "method": "GET", "url": "https://example.com/items" },
                  "incremental": {
                    "enabled": true,
                    "mode": "rolling_window",
                    "rolling_window": {
                      "response_path": "$.updated_at",
                      "start_param": "from",
                      "end_param": "to",
                      "request_target": "query",
                      "format": "iso_instant",
                      "overlap": "10m"
                    }
                  }
                }
                """), objectMapper);

        RuntimeConnectorConfig.IncrementalSettings incremental = config.incremental();
        assertThat(incremental.enabled()).isTrue();
        assertThat(incremental.isRollingWindow()).isTrue();
        assertThat(incremental.responsePath()).isEqualTo("$.updated_at");
        assertThat(incremental.requestParam()).isEqualTo("from");
        assertThat(incremental.requestEndParam()).isEqualTo("to");
        assertThat(incremental.requestTarget()).isEqualTo("query");
        assertThat(incremental.timeFormat()).isEqualTo("iso_instant");
        assertThat(incremental.overlap()).isEqualTo("10m");
    }
}
