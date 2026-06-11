package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectorImportRequestDto(
        @NotBlank String id,
        @NotBlank String name,
        @NotBlank String mode,
        @NotNull JsonNode config,
        boolean overwrite,
        boolean publishAfterImport
) {
}
