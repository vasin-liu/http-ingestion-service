package com.pcitech.http.ingestion.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.pcitech.http.ingestion.core.dto.FieldMappingDto;
import com.pcitech.http.ingestion.core.dto.GenerateSampleRequestDto;
import com.pcitech.http.ingestion.core.dto.InferMappingsRequestDto;
import com.pcitech.http.ingestion.core.dto.InferSchemaRequestDto;
import com.pcitech.http.ingestion.core.dto.JsonPathSuggestRequestDto;
import com.pcitech.http.ingestion.core.dto.TransformPreviewRequestDto;
import com.pcitech.http.ingestion.core.service.PreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/preview")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("/transform")
    public List<Map<String, Object>> previewTransform(@Valid @RequestBody TransformPreviewRequestDto request) {
        return previewService.previewTransform(
                request.responseBody(),
                request.transform(),
                request.limitOrDefault()
        );
    }

    @PostMapping("/jsonpath")
    public List<Map<String, Object>> suggestJsonPath(@Valid @RequestBody JsonPathSuggestRequestDto request) {
        return previewService.suggestPaths(request.responseBody(), request.limitOrDefault());
    }

    @PostMapping("/infer-mappings")
    public List<FieldMappingDto> inferMappings(@RequestBody InferMappingsRequestDto request) {
        return previewService.inferMappings(request.responseBody(), request.inputRoot(), request.recordSchema());
    }

    @PostMapping("/generate-sample")
    public Map<String, String> generateSample(@RequestBody GenerateSampleRequestDto request) {
        String body = previewService.generateSample(
                request.recordSchema(),
                request.modeOrDefault(),
                request.realSample()
        );
        return Map.of("body", body);
    }

    @PostMapping("/infer-schema")
    public JsonNode inferSchema(@RequestBody InferSchemaRequestDto request) {
        return previewService.inferSchema(request.responseBody(), request.sampleJson(), request.inputRoot());
    }
}
