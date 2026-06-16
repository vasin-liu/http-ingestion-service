package com.pcitech.http.ingestion.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigParserMonotonicIdTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseMonotonicIdIncremental() throws Exception {
        String json = """
                {
                  "incremental": {
                    "enabled": true,
                    "mode": "monotonic_id",
                    "monotonic_id": {
                      "response_path": "$.recordId",
                      "request_param": "after_id",
                      "request_target": "query"
                    }
                  }
                }
                """;
        RuntimeConnectorConfig config = RuntimeConfigParser.parse(objectMapper.readTree(json), objectMapper);
        var incremental = config.incremental();
        assertThat(incremental.enabled()).isTrue();
        assertThat(incremental.isMonotonicId()).isTrue();
        assertThat(incremental.responsePath()).isEqualTo("$.recordId");
        assertThat(incremental.requestParam()).isEqualTo("after_id");
        assertThat(incremental.requestTarget()).isEqualTo("query");
    }
}
