package com.pcitech.http.ingestion.core.domain;

import java.time.Instant;

public record Connector(
        String id,
        String name,
        String mode,
        Instant createdAt,
        Instant updatedAt
) {
}
