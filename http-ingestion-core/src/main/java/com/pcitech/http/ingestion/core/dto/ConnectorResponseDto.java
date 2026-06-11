package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record ConnectorResponseDto(
        String id,
        String name,
        String mode,
        JsonNode draftConfig,
        Integer latestPublishedVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
