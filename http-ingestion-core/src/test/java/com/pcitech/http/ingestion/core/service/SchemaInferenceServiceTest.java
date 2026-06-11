package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.dto.FieldMappingDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaInferenceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SchemaInferenceService service = new SchemaInferenceService(
            new com.pcitech.http.ingestion.core.engine.JsonPathSupport(objectMapper),
            objectMapper
    );

    @Test
    void inferMappingsFromSample_usesIdentityFieldNames() {
        String body = """
                {"results":[{"recordId":"rec-1","plateNum":"粤A12345"}],"totalCount":1}
                """;
        List<FieldMappingDto> mappings = service.inferMappings(body, "$.results", null);
        assertThat(mappings).extracting(FieldMappingDto::target).containsExactly("recordId", "plateNum");
        assertThat(mappings).extracting(FieldMappingDto::source).containsExactly("$.recordId", "$.plateNum");
    }

    @Test
    void inferSchemaFromResponse_includesEnvelopeAndRecordFields() throws Exception {
        String body = """
                {"totalCount":1000,"results":[{"recordId":"rec-1","plateNum":"粤A12345"}],"nextPage":2}
                """;
        var schema = service.inferSchemaFromResponse(body, "$.results");
        assertThat(schema.path("input_root").asText()).isEqualTo("$.results");
        assertThat(schema.path("record").path("properties").path("recordId").path("type").asText()).isEqualTo("string");
        assertThat(schema.path("envelope").path("totalCount").path("type").asText()).isEqualTo("long");
    }

    @Test
    void inferSchemaFromSample_detectsNestedArray() throws Exception {
        String sample = """
                {"version":"2.0","data":[{"jqbh":"jq-1","evcc":"2025-06-01 08:00:00"}],"states":{"total":1}}
                """;
        var schema = service.inferSchemaFromSample(sample);
        assertThat(schema.path("input_root").asText()).isEqualTo("$.data");
        assertThat(schema.path("record").path("properties").path("jqbh").path("type").asText()).isEqualTo("string");
    }

    @Test
    void generateSampleResponse_randomMode_buildsEnvelopeAndRecords() throws Exception {
        var schema = objectMapper.readTree("""
                {
                  "input_root": "$.results",
                  "record": {
                    "type": "object",
                    "properties": {
                      "recordId": {"type": "string", "example": "rec-1"}
                    }
                  },
                  "envelope": {
                    "totalCount": {"type": "integer", "example": 10}
                  }
                }
                """);
        String body = service.generateSampleResponse(schema, "random", null);
        var json = objectMapper.readTree(body);
        assertThat(json.path("totalCount").isNumber()).isTrue();
        assertThat(json.path("results").isArray()).isTrue();
        assertThat(json.path("results").get(0).path("recordId").asText()).isNotBlank();
    }
}
