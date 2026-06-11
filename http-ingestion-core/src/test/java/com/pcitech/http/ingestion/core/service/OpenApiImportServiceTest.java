package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.dto.OpenApiOperationDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseRequestDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiImportServiceTest {

    private final OpenApiImportService service = new OpenApiImportService(
            new ObjectMapper(),
            WebClient.builder()
    );

    @Test
    void parse_extractsOperationsRequestAndResponseSchemas() throws Exception {
        String spec = new String(
                getClass().getResourceAsStream("/openapi/sample-users.json").readAllBytes(),
                StandardCharsets.UTF_8
        );

        OpenApiParseResultDto result = service.parse(new OpenApiParseRequestDto(spec, null));

        assertThat(result.serverUrls()).containsExactly("https://api.example.com/v1");
        assertThat(result.operations()).hasSize(2);

        OpenApiOperationDto listUsers = result.operations().stream()
                .filter(op -> "listUsers".equals(op.operationId()))
                .findFirst()
                .orElseThrow();
        assertThat(listUsers.method()).isEqualTo("GET");
        assertThat(listUsers.path()).isEqualTo("/users");
        assertThat(listUsers.suggestedInputRoot()).isEqualTo("$.data");
        assertThat(listUsers.requestSchema().path("query").path("properties").path("page").path("type").asText())
                .isEqualTo("integer");
        assertThat(listUsers.responseSchema().path("input_root").asText()).isEqualTo("$.data");
        assertThat(listUsers.responseSchema().path("record").path("properties").path("id").path("type").asText())
                .isEqualTo("integer");
        assertThat(listUsers.httpConfig().path("url").asText()).isEqualTo("https://api.example.com/v1/users");
        assertThat(listUsers.httpConfig().path("method").asText()).isEqualTo("GET");

        OpenApiOperationDto createUser = result.operations().stream()
                .filter(op -> "createUser".equals(op.operationId()))
                .findFirst()
                .orElseThrow();
        assertThat(createUser.httpConfig().path("body_type").asText()).isEqualTo("json");
        assertThat(createUser.httpConfig().path("body").path("name").asText()).isEqualTo("Alice");
    }

    @Test
    void parse_supportsSwagger2DefinitionsAndBodyParameters() throws Exception {
        String spec = new String(
                getClass().getResourceAsStream("/openapi/sample-swagger2.json").readAllBytes(),
                StandardCharsets.UTF_8
        );

        OpenApiParseResultDto result = service.parse(new OpenApiParseRequestDto(spec, null));

        OpenApiOperationDto listItems = result.operations().stream()
                .filter(op -> "listItems".equals(op.operationId()))
                .findFirst()
                .orElseThrow();
        assertThat(listItems.serverUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(listItems.suggestedInputRoot()).isEqualTo("$.data");
        assertThat(listItems.responseSchema().path("record").path("properties").path("id").path("type").asText())
                .isEqualTo("integer");
        assertThat(listItems.requestSchema().path("query").path("properties").path("page").path("type").asText())
                .isEqualTo("integer");

        OpenApiOperationDto createItem = result.operations().stream()
                .filter(op -> "createItem".equals(op.operationId()))
                .findFirst()
                .orElseThrow();
        assertThat(createItem.httpConfig().path("body_type").asText()).isEqualTo("json");
        assertThat(createItem.requestSchema().path("body").path("properties").path("name").path("type").asText())
                .isEqualTo("string");
    }
}
