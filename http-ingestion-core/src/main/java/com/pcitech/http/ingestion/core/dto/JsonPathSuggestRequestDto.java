package com.pcitech.http.ingestion.core.dto;

public record JsonPathSuggestRequestDto(
        String responseBody,
        Integer limit
) {
    public int limitOrDefault() {
        return limit == null || limit <= 0 ? 30 : limit;
    }
}
