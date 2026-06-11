package com.pcitech.http.ingestion.core.dto;

import java.util.List;

public record IngestionStatsDto(
        long totalJobs,
        long successJobs,
        long failedJobs,
        long recordsOk,
        long recordsFailed,
        List<ConnectorJobStatsDto> byConnector,
        List<JobRunDto> recentJobs
) {
}
