package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConnectorRequestDto(
        @NotBlank String id,
        @NotBlank String name,
        @Pattern(regexp = "pull|push|both") String mode,
        JsonNode config
) {
}
