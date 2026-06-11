package com.pcitech.http.ingestion.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransformPipelineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransformPipeline pipeline = new TransformPipeline(new JsonPathSupport(objectMapper), objectMapper);

    @Test
    void transformTolerant_skipsFailedRecords() {
        RuntimeConnectorConfig.TransformSettings transform = new RuntimeConnectorConfig.TransformSettings(
                "$",
                List.of(new RuntimeConnectorConfig.TransformStep(
                        "map_fields",
                        null,
                        List.of(
                                new RuntimeConnectorConfig.FieldMapping("user_id", "$.id", "long"),
                                new RuntimeConnectorConfig.FieldMapping("user_name", "$.name", "string")
                        ),
                        null
                ))
        );

        List<Object> records = List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", "bad", "name", "Bob")
        );

        TransformPipeline.TransformResult result = pipeline.transformTolerant(records, transform, 5);

        assertThat(result.records()).hasSize(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).recordIndex()).isEqualTo(1);
    }

    @Test
    void mapFields_convertsDatetimeWithSourceFormat() {
        RuntimeConnectorConfig.TransformSettings transform = new RuntimeConnectorConfig.TransformSettings(
                "$",
                List.of(new RuntimeConnectorConfig.TransformStep(
                        "map_fields",
                        null,
                        List.of(new RuntimeConnectorConfig.FieldMapping(
                                "cap_time",
                                "$.capTime",
                                "datetime",
                                "dahua_utc",
                                "iso_instant"
                        )),
                        null
                ))
        );
        List<Object> records = List.of(Map.of("capTime", "20250601T080000Z"));
        List<Map<String, Object>> result = pipeline.transform(records, transform);
        assertThat(result.get(0).get("cap_time")).isEqualTo("2025-06-01T08:00:00Z");
    }

    @Test
    void mapFields_withoutMappings_copiesAllFields() {
        RuntimeConnectorConfig.TransformSettings transform = new RuntimeConnectorConfig.TransformSettings(
                "$",
                List.of(new RuntimeConnectorConfig.TransformStep("map_fields", null, List.of(), null))
        );
        List<Object> records = List.of(Map.of("id", 1, "name", "Alice"));
        List<Map<String, Object>> result = pipeline.transform(records, transform);
        assertThat(result).containsExactly(Map.of("id", 1, "name", "Alice"));
    }
}
