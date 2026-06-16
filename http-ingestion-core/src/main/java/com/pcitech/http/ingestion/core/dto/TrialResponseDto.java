package com.pcitech.http.ingestion.core.dto;

public record TrialResponseDto(
        int statusCode,
        long durationMs,
        String body,
        boolean truncated,
        Long bodyLength,
        String error,
        java.util.Map<String, String> responseHeaders
) {
    public TrialResponseDto {
        responseHeaders = responseHeaders == null ? java.util.Map.of() : java.util.Map.copyOf(responseHeaders);
    }

    public static TrialResponseDto success(int statusCode, long durationMs, String body, boolean truncated, long bodyLength) {
        return success(statusCode, durationMs, body, truncated, bodyLength, java.util.Map.of());
    }

    public static TrialResponseDto success(
            int statusCode,
            long durationMs,
            String body,
            boolean truncated,
            long bodyLength,
            java.util.Map<String, String> responseHeaders
    ) {
        return new TrialResponseDto(statusCode, durationMs, body, truncated, bodyLength, null, responseHeaders);
    }

    public static TrialResponseDto failure(String error, long durationMs) {
        return new TrialResponseDto(0, durationMs, null, false, null, error, java.util.Map.of());
    }
}
