package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ConnectorTemplateDto(
        String id,
        String name,
        String description,
        String mode,
        String category,
        JsonNode responseSchema,
        JsonNode sampleResponse,
        JsonNode requestSchema,
        JsonNode config
) {
}
