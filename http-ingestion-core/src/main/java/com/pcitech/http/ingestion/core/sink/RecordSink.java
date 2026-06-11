package com.pcitech.http.ingestion.core.sink;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;

import java.util.List;
import java.util.Map;

public interface RecordSink {

    boolean isAvailable();

    default boolean supports(RuntimeConnectorConfig.SinkSettings sink) {
        if (sink == null) {
            return false;
        }
        String type = sink.type();
        if (type == null || type.isBlank()) {
            type = "postgresql";
        }
        return supportsType(type);
    }

    default boolean supportsType(String type) {
        return "postgresql".equalsIgnoreCase(type);
    }

    int write(List<Map<String, Object>> records, RuntimeConnectorConfig.SinkSettings sink) throws Exception;
}
