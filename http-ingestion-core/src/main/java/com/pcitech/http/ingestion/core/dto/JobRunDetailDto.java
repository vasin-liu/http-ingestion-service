package com.pcitech.http.ingestion.core.dto;

public record JobRunDetailDto(
        Long id,
        String stage,
        Integer pageNumber,
        Integer recordIndex,
        String message,
        String sampleJson
) {
}
