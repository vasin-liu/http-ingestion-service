package com.pcitech.http.ingestion.core.domain;

import java.time.Instant;

public record WatermarkState(Instant timestamp, String lastId) {

    public static WatermarkState empty() {
        return new WatermarkState(null, null);
    }

    public boolean hasTimestamp() {
        return timestamp != null;
    }

    public boolean hasLastId() {
        return lastId != null && !lastId.isBlank();
    }
}
