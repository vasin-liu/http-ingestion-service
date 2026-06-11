package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.dto.OpenApiParseRequestDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseResultDto;
import com.pcitech.http.ingestion.core.service.OpenApiImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiController {

    private final OpenApiImportService openApiImportService;

    public OpenApiController(OpenApiImportService openApiImportService) {
        this.openApiImportService = openApiImportService;
    }

    @PostMapping("/parse")
    public OpenApiParseResultDto parse(@RequestBody OpenApiParseRequestDto request) {
        return openApiImportService.parse(request);
    }
}
