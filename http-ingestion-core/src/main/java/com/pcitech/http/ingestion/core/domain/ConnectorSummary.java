package com.pcitech.http.ingestion.core.domain;

import java.time.Instant;

public record ConnectorSummary(
        String id,
        String name,
        String mode,
        boolean hasDraft,
        Integer latestPublishedVersion,
        Instant updatedAt
) {
}
