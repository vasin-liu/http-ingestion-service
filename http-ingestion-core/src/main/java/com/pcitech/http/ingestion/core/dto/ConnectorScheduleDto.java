package com.pcitech.http.ingestion.core.dto;

public record ConnectorScheduleDto(
        String connectorId,
        String scheduleType,
        String expression,
        boolean enabled,
        boolean registered,
        boolean paused
) {
}
