package com.pcitech.http.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.dto.ConnectorResponseDto;
import com.pcitech.http.ingestion.core.dto.OpenApiOperationDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseRequestDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseResultDto;
import com.pcitech.http.ingestion.support.AbstractIntegrationE2ETest;
import com.pcitech.http.ingestion.support.OpenApiImportTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OpenApiImportE2ETest extends AbstractIntegrationE2ETest {

    @RegisterExtension
    static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetEnvironment() throws Exception {
        resetIntegrationEnvironment();
    }

    @Test
    void parseOas3Inline_returnsOperationsWithSchemas() throws Exception {
        String spec = OpenApiImportTestSupport.loadClasspathSpec(getClass(), "/openapi/sample-users.json");

        ResponseEntity<OpenApiParseResultDto> response = restTemplate.postForEntity(
                "/api/openapi/parse",
                new OpenApiParseRequestDto(spec, null),
                OpenApiParseResultDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().serverUrls()).containsExactly("https://api.example.com/v1");
        assertThat(response.getBody().operations()).hasSize(2);

        OpenApiOperationDto listUsers = findOperation(response.getBody().operations(), "listUsers");
        assertThat(listUsers.method()).isEqualTo("GET");
        assertThat(listUsers.path()).isEqualTo("/users");
        assertThat(listUsers.suggestedInputRoot()).isEqualTo("$.data");
        assertThat(listUsers.requestSchema().path("query").path("properties").path("page").path("type").asText())
                .isEqualTo("integer");
        assertThat(listUsers.suggestedPagination()).isNotNull();
        assertThat(listUsers.suggestedPagination().path("page_param").asText()).isEqualTo("page");
        assertThat(listUsers.suggestedPagination().path("total_count").path("json_path").asText())
                .isEqualTo("$.meta.total");
        assertThat(listUsers.responseSchema().path("record").path("properties").path("id").path("type").asText())
                .isEqualTo("integer");
    }

    @Test
    void parseSwagger2FromUrl_resolvesDefinitions() throws Exception {
        String spec = OpenApiImportTestSupport.loadClasspathSpec(getClass(), "/openapi/sample-swagger2.json");
        WIREMOCK.stubFor(get(urlEqualTo("/v2/api-docs"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(spec)));

        ResponseEntity<OpenApiParseResultDto> response = restTemplate.postForEntity(
                "/api/openapi/parse",
                new OpenApiParseRequestDto(null, WIREMOCK.baseUrl() + "/v2/api-docs"),
                OpenApiParseResultDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        OpenApiOperationDto listItems = findOperation(response.getBody().operations(), "listItems");
        assertThat(listItems.serverUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(listItems.responseSchema().path("record").path("properties").path("id").path("type").asText())
                .isEqualTo("integer");
        assertThat(listItems.requestSchema().path("query").path("properties").path("page").path("type").asText())
                .isEqualTo("integer");
    }

    @Test
    void batchCreateFromParse_persistsReadableIdsAndOpenApiMeta() throws Exception {
        String spec = OpenApiImportTestSupport.loadClasspathSpec(getClass(), "/openapi/sample-users.json");
        ResponseEntity<OpenApiParseResultDto> parsed = restTemplate.postForEntity(
                "/api/openapi/parse",
                new OpenApiParseRequestDto(spec, null),
                OpenApiParseResultDto.class
        );
        assertThat(parsed.getBody()).isNotNull();
        List<OpenApiOperationDto> operations = parsed.getBody().operations();
        assertThat(operations).hasSizeGreaterThanOrEqualTo(2);

        String firstId = OpenApiImportTestSupport.connectorIdFromOperation(operations.get(0), "1");
        String secondId = OpenApiImportTestSupport.connectorIdFromOperation(operations.get(1), "2");

        ConnectorRequestDto firstRequest = OpenApiImportTestSupport.toConnectorRequest(
                objectMapper, operations.get(0), firstId);
        ConnectorRequestDto secondRequest = OpenApiImportTestSupport.toConnectorRequest(
                objectMapper, operations.get(1), secondId);

        assertThat(firstId).startsWith("openapi-");
        assertThat(secondId).startsWith("openapi-");
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(firstId).endsWith("-1");
        assertThat(secondId).endsWith("-2");

        ResponseEntity<ConnectorResponseDto> createdFirst = restTemplate.postForEntity(
                "/api/connectors", firstRequest, ConnectorResponseDto.class);
        ResponseEntity<ConnectorResponseDto> createdSecond = restTemplate.postForEntity(
                "/api/connectors", secondRequest, ConnectorResponseDto.class);

        assertThat(createdFirst.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(createdSecond.getStatusCode().is2xxSuccessful()).isTrue();

        ConnectorResponseDto fetchedFirst = restTemplate.getForObject("/api/connectors/" + firstId, ConnectorResponseDto.class);
        ConnectorResponseDto fetchedSecond = restTemplate.getForObject("/api/connectors/" + secondId, ConnectorResponseDto.class);

        assertThat(fetchedFirst).isNotNull();
        assertThat(fetchedSecond).isNotNull();

        JsonNode firstMeta = OpenApiImportTestSupport.requireOpenApiMeta(fetchedFirst.draftConfig());
        JsonNode secondMeta = OpenApiImportTestSupport.requireOpenApiMeta(fetchedSecond.draftConfig());

        assertThat(firstMeta.path("operation_id").asText()).isEqualTo(operations.get(0).operationId());
        assertThat(secondMeta.path("operation_id").asText()).isEqualTo(operations.get(1).operationId());
        assertThat(firstMeta.path("request_schema").path("query").path("properties").isObject()).isTrue();
        assertThat(firstMeta.path("response_schema").path("record").path("properties").isObject()).isTrue();
        assertThat(secondMeta.path("request_schema").path("body").path("properties").isObject()).isTrue();
        assertThat(secondMeta.path("response_schema").path("record").path("properties").isObject()).isTrue();
    }

    private static OpenApiOperationDto findOperation(List<OpenApiOperationDto> operations, String operationId) {
        return operations.stream()
                .filter(operation -> operationId.equals(operation.operationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Operation not found: " + operationId));
    }
}
