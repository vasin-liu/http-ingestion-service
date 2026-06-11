package com.pcitech.http.ingestion.core.domain;

import java.time.Instant;

public record JobRun(
        Long id,
        String connectorId,
        Long connectorVersionId,
        String runType,
        String status,
        Instant startedAt,
        Instant finishedAt,
        int recordsOk,
        int recordsFailed,
        String errorMessage
) {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
}
