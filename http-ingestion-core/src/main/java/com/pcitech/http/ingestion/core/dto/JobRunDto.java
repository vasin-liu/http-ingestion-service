package com.pcitech.http.ingestion.core.dto;

import java.time.Instant;

public record JobRunDto(
        Long id,
        String connectorId,
        String runType,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        int recordsOk,
        int recordsFailed,
        String errorMessage
) {
}
