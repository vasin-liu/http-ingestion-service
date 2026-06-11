package com.pcitech.http.ingestion.core.repository;

import java.util.Optional;

public record ConnectorScheduleRow(
        String scheduleType,
        String expression,
        boolean enabled
) {
}
