package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record InferMappingsRequestDto(
        String responseBody,
        String inputRoot,
        JsonNode recordSchema
) {
}
