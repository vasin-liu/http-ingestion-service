package com.pcitech.http.ingestion.core.dto;

public record ConnectorJobStatsDto(
        String connectorId,
        long totalJobs,
        long successJobs,
        long failedJobs,
        long recordsOk,
        long recordsFailed
) {
}
