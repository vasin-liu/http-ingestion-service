package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record TransformPreviewRequestDto(
        String responseBody,
        JsonNode transform,
        Integer limit
) {
    public int limitOrDefault() {
        return limit == null || limit <= 0 ? 10 : limit;
    }
}
