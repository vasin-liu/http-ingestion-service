package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiPaginationInferenceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void infersPagePageSizeWithTotalFromMeta() throws Exception {
        JsonNode request = mapper.readTree("""
                {
                  "query": {
                    "properties": {
                      "page": { "type": "integer" },
                      "page_size": { "type": "integer" }
                    }
                  }
                }
                """);
        JsonNode response = mapper.readTree("""
                {
                  "envelope": {
                    "properties": {
                      "data": { "type": "array" },
                      "meta": {
                        "type": "object",
                        "properties": {
                          "total": { "type": "integer" }
                        }
                      }
                    }
                  }
                }
                """);

        JsonNode pagination = OpenApiPaginationInference.infer(request, response, "GET");

        assertThat(pagination).isNotNull();
        assertThat(pagination.path("strategy").asText()).isEqualTo("page_page_size");
        assertThat(pagination.path("page_param").asText()).isEqualTo("page");
        assertThat(pagination.path("page_size_param").asText()).isEqualTo("page_size");
        assertThat(pagination.path("total_count").path("source").asText()).isEqualTo("json_path");
        assertThat(pagination.path("total_count").path("json_path").asText()).isEqualTo("$.meta.total");
    }

    @Test
    void infersPageOnlyWithDefaultPageSizeParam() throws Exception {
        JsonNode request = mapper.readTree("""
                {
                  "query": {
                    "properties": {
                      "page": { "type": "integer" }
                    }
                  }
                }
                """);
        JsonNode response = mapper.readTree("""
                {
                  "envelope": {
                    "properties": {
                      "data": { "type": "array" },
                      "total": { "type": "integer" }
                    }
                  }
                }
                """);

        JsonNode pagination = OpenApiPaginationInference.infer(request, response, "GET");

        assertThat(pagination).isNotNull();
        assertThat(pagination.path("strategy").asText()).isEqualTo("page_page_size");
        assertThat(pagination.path("page_param").asText()).isEqualTo("page");
        assertThat(pagination.path("page_size_param").asText()).isEqualTo("page_size");
        assertThat(pagination.path("total_count").path("json_path").asText()).isEqualTo("$.total");
    }

    @Test
    void infersOffsetLimit() throws Exception {
        JsonNode request = mapper.readTree("""
                {
                  "query": {
                    "properties": {
                      "offset": { "type": "integer" },
                      "limit": { "type": "integer" }
                    }
                  }
                }
                """);
        JsonNode response = mapper.readTree("""
                {
                  "envelope": {
                    "properties": {
                      "items": { "type": "array" }
                    }
                  }
                }
                """);

        JsonNode pagination = OpenApiPaginationInference.infer(request, response, "GET");

        assertThat(pagination).isNotNull();
        assertThat(pagination.path("strategy").asText()).isEqualTo("offset_limit");
        assertThat(pagination.path("page_param").asText()).isEqualTo("offset");
        assertThat(pagination.path("page_size_param").asText()).isEqualTo("limit");
        assertThat(pagination.path("page_start").asInt()).isZero();
    }

    @Test
    void infersCursorFromQueryAndResponseMeta() throws Exception {
        JsonNode request = mapper.readTree("""
                {
                  "query": {
                    "properties": {
                      "cursor": { "type": "string" }
                    }
                  }
                }
                """);
        JsonNode response = mapper.readTree("""
                {
                  "envelope": {
                    "properties": {
                      "data": { "type": "array" },
                      "meta": {
                        "type": "object",
                        "properties": {
                          "nextCursor": { "type": "string" },
                          "hasMore": { "type": "boolean" }
                        }
                      }
                    }
                  }
                }
                """);

        JsonNode pagination = OpenApiPaginationInference.infer(request, response, "GET");

        assertThat(pagination).isNotNull();
        assertThat(pagination.path("strategy").asText()).isEqualTo("cursor");
        assertThat(pagination.path("location").asText()).isEqualTo("query");
        assertThat(pagination.path("cursor_param").asText()).isEqualTo("cursor");
        assertThat(pagination.path("cursor_response_path").asText()).isEqualTo("$.meta.nextCursor");
        assertThat(pagination.path("has_more_path").asText()).isEqualTo("$.meta.hasMore");
    }

    @Test
    void returnsNullForPostOperations() {
        ObjectNode request = mapper.createObjectNode();
        ObjectNode response = mapper.createObjectNode();

        assertThat(OpenApiPaginationInference.infer(request, response, "POST")).isNull();
    }

    @Test
    void returnsNullWhenNoPaginationSignals() throws Exception {
        JsonNode request = mapper.readTree("""
                {
                  "query": {
                    "properties": {
                      "status": { "type": "string" }
                    }
                  }
                }
                """);
        JsonNode response = mapper.readTree("""
                {
                  "envelope": {
                    "properties": {
                      "data": { "type": "array" }
                    }
                  }
                }
                """);

        assertThat(OpenApiPaginationInference.infer(request, response, "GET")).isNull();
    }
}
