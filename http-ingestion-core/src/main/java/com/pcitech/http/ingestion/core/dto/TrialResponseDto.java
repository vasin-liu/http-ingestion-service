package com.pcitech.http.ingestion.core.dto;

public record TrialResponseDto(
        int statusCode,
        long durationMs,
        String body,
        boolean truncated,
        Long bodyLength,
        String error
) {
    public static TrialResponseDto success(int statusCode, long durationMs, String body, boolean truncated, long bodyLength) {
        return new TrialResponseDto(statusCode, durationMs, body, truncated, bodyLength, null);
    }

    public static TrialResponseDto failure(String error, long durationMs) {
        return new TrialResponseDto(0, durationMs, null, false, null, error);
    }
}
