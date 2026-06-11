package com.pcitech.http.ingestion.core.dto;

public record OpenApiParseRequestDto(
        String spec,
        String specUrl
) {
}
