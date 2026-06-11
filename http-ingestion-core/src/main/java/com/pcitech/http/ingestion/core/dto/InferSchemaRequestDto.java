package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record InferSchemaRequestDto(
        String responseBody,
        String sampleJson,
        String inputRoot
) {
    public String bodyOrSample() {
        if (responseBody != null && !responseBody.isBlank()) {
            return responseBody;
        }
        return sampleJson;
    }
}
