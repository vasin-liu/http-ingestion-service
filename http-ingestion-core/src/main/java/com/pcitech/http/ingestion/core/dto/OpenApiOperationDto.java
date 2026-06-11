package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenApiOperationDto(
        String operationId,
        String method,
        String path,
        String summary,
        String serverUrl,
        String suggestedInputRoot,
        JsonNode requestSchema,
        JsonNode responseSchema,
        JsonNode httpConfig
) {
}
