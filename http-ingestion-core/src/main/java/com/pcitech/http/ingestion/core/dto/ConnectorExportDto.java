package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record ConnectorExportDto(
        String exportVersion,
        String id,
        String name,
        String mode,
        JsonNode config,
        Integer latestPublishedVersion,
        Instant exportedAt
) {
}
