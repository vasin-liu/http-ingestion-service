package com.pcitech.http.ingestion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.dto.OpenApiOperationDto;

public final class OpenApiImportTestSupport {

    private OpenApiImportTestSupport() {
    }

    public static String connectorIdFromOperation(OpenApiOperationDto operation, String suffix) {
        String raw = operation.operationId() != null && !operation.operationId().isBlank()
                ? operation.operationId()
                : operation.method().toLowerCase() + "-" + operation.path().replaceFirst("^/", "");
        String slug = raw.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 48) {
            slug = slug.substring(0, 48);
        }
        String base = "openapi-" + (slug.isEmpty() ? "operation" : slug);
        return suffix == null || suffix.isBlank() ? base : base + "-" + suffix;
    }

    public static ConnectorRequestDto toConnectorRequest(
            ObjectMapper objectMapper,
            OpenApiOperationDto operation,
            String connectorId
    ) {
        ObjectNode config = objectMapper.createObjectNode();
        config.set("http", operation.httpConfig().deepCopy());

        ObjectNode transform = config.putObject("transform");
        String inputRoot = operation.suggestedInputRoot() != null && !operation.suggestedInputRoot().isBlank()
                ? operation.suggestedInputRoot()
                : "$";
        transform.put("input_root", inputRoot);
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        step.putArray("mappings");

        ObjectNode meta = config.putObject("openapi_meta");
        meta.set("request_schema", operation.requestSchema());
        meta.set("response_schema", operation.responseSchema());
        meta.put("operation_id", operation.operationId());
        meta.put("path", operation.path());
        meta.put("method", operation.method());

        ObjectNode sink = config.putObject("sink");
        sink.put("type", "postgresql");
        ObjectNode target = sink.putObject("target");
        target.put("schema", "public");
        target.put("table", "openapi_import_e2e");
        sink.putArray("keys").add("id");

        config.putObject("pagination");
        if (operation.suggestedPagination() != null && !operation.suggestedPagination().isNull()) {
            config.set("pagination", operation.suggestedPagination().deepCopy());
        }
        config.putObject("incremental").put("enabled", false);
        config.putObject("schedule").put("enabled", false);

        String name = operation.summary() != null && !operation.summary().isBlank()
                ? operation.summary()
                : operation.method() + " " + operation.path();
        return new ConnectorRequestDto(connectorId, name, "pull", config);
    }

    public static String loadClasspathSpec(Class<?> anchor, String resourcePath) throws Exception {
        try (var stream = anchor.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + resourcePath);
            }
            return new String(stream.readAllBytes());
        }
    }

    public static JsonNode requireOpenApiMeta(JsonNode draftConfig) {
        JsonNode meta = draftConfig.path("openapi_meta");
        if (meta.isMissingNode() || meta.isNull()) {
            throw new AssertionError("draftConfig.openapi_meta is missing");
        }
        return meta;
    }
}
