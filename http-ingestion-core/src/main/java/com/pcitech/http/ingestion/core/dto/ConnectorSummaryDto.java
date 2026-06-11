package com.pcitech.http.ingestion.core.dto;

import java.time.Instant;

public record ConnectorSummaryDto(
        String id,
        String name,
        String mode,
        boolean hasDraft,
        Integer latestPublishedVersion,
        Instant updatedAt
) {
}
