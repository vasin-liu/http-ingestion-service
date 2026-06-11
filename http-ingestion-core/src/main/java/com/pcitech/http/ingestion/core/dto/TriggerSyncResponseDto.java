package com.pcitech.http.ingestion.core.dto;

public record TriggerSyncResponseDto(
        long jobRunId,
        String status
) {
}
