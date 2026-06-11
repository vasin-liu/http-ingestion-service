package com.pcitech.http.ingestion.core.domain;

public record JobRunDetail(
        long id,
        long jobRunId,
        String stage,
        Integer pageNumber,
        Integer recordIndex,
        String message,
        String sampleJson
) {
}
