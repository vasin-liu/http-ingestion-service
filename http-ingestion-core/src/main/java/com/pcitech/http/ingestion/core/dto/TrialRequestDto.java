package com.pcitech.http.ingestion.core.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record TrialRequestDto(
        @NotBlank String method,
        @NotBlank String url,
        Map<String, String> headers,
        Map<String, String> query,
        String bodyType,
        String body,
        Map<String, String> form,
        Integer timeoutMs
) {
    public TrialRequestDto {
        headers = headers == null ? Map.of() : headers;
        query = query == null ? Map.of() : query;
        form = form == null ? Map.of() : form;
        if (timeoutMs == null) {
            timeoutMs = 30000;
        }
        if (bodyType == null || bodyType.isBlank()) {
            if (body != null && !body.isBlank()) {
                bodyType = "json";
            } else if (!form.isEmpty()) {
                bodyType = "form-urlencoded";
            } else {
                bodyType = "none";
            }
        }
    }
}
