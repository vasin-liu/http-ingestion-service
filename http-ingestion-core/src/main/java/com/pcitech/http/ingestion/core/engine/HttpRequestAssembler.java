package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.dto.TrialRequestDto;

import java.util.Map;

public final class HttpRequestAssembler {

    private HttpRequestAssembler() {
    }

    public static TrialRequestDto fromSettings(
            RuntimeConnectorConfig.HttpSettings http,
            Map<String, String> query,
            String composedJsonBody
    ) {
        return fromSettings(http, http.url(), query, composedJsonBody);
    }

    public static TrialRequestDto fromSettings(
            RuntimeConnectorConfig.HttpSettings http,
            String requestUrl,
            Map<String, String> query,
            String composedJsonBody
    ) {
        String bodyType = resolveBodyType(http);
        if ("json".equals(bodyType)) {
            return new TrialRequestDto(
                    http.method(),
                    requestUrl,
                    http.headers(),
                    query,
                    "json",
                    composedJsonBody,
                    Map.of(),
                    http.timeoutMs()
            );
        }
        if ("form-urlencoded".equals(bodyType)) {
            return new TrialRequestDto(
                    http.method(),
                    requestUrl,
                    http.headers(),
                    query,
                    "form-urlencoded",
                    null,
                    http.form(),
                    http.timeoutMs()
            );
        }
        return new TrialRequestDto(
                http.method(),
                requestUrl,
                http.headers(),
                query,
                "none",
                null,
                Map.of(),
                http.timeoutMs()
        );
    }

    static String resolveBodyType(RuntimeConnectorConfig.HttpSettings http) {
        if (http.bodyType() != null && !http.bodyType().isBlank()) {
            return http.bodyType();
        }
        if (http.bodyJson() != null && !http.bodyJson().isBlank()) {
            return "json";
        }
        if (http.form() != null && !http.form().isEmpty()) {
            return "form-urlencoded";
        }
        return "none";
    }
}
