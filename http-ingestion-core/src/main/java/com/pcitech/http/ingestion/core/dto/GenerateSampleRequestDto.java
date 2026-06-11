package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record GenerateSampleRequestDto(
        JsonNode recordSchema,
        String mode,
        JsonNode realSample
) {
    public String modeOrDefault() {
        return mode == null || mode.isBlank() ? "random" : mode;
    }
}
