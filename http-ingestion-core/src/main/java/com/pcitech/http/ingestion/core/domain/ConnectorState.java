package com.pcitech.http.ingestion.core.domain;

import java.time.Instant;

public record ConnectorState(
        String connectorId,
        String watermarkJson,
        Instant updatedAt
) {
}
