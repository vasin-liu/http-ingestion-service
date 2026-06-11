package com.pcitech.http.ingestion.core.config;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record ConnectorConfig(
        String mode,
        HttpConfig http,
        JsonNode raw
) {
    public record HttpConfig(
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> query,
            Integer timeoutMs
    ) {
        public HttpConfig {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            query = query == null ? Map.of() : Map.copyOf(query);
            if (timeoutMs == null) {
                timeoutMs = 30000;
            }
        }
    }
}
