package com.pcitech.http.ingestion.core.dto;

import java.time.Instant;

public record ConnectorStateDto(
        String connectorId,
        String watermarkJson,
        Instant updatedAt
) {
}
