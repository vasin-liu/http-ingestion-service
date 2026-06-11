package com.pcitech.http.ingestion.core.dto;

import java.util.List;

public record OpenApiParseResultDto(
        List<String> serverUrls,
        List<OpenApiOperationDto> operations
) {
}
