package com.pcitech.http.ingestion.core.domain;

import java.time.Instant;

public record ConnectorVersion(
        Long id,
        String connectorId,
        Integer versionNumber,
        String status,
        String configJson,
        Instant createdAt,
        Instant publishedAt
) {
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
}
