package com.pcitech.http.ingestion.core.dto;

public record FieldMappingDto(
        String target,
        String source,
        String type,
        String sourceFormat,
        String targetFormat
) {
    public FieldMappingDto(String target, String source, String type) {
        this(target, source, type, null, null);
    }
}
