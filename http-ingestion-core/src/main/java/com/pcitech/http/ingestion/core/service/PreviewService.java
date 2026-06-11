package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.dto.FieldMappingDto;
import com.pcitech.http.ingestion.core.engine.JsonPathSupport;
import com.pcitech.http.ingestion.core.engine.TransformPipeline;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PreviewService {

    private final JsonPathSupport jsonPathSupport;
    private final TransformPipeline transformPipeline;
    private final ObjectMapper objectMapper;
    private final SchemaInferenceService schemaInferenceService;

    public PreviewService(
            JsonPathSupport jsonPathSupport,
            TransformPipeline transformPipeline,
            ObjectMapper objectMapper,
            SchemaInferenceService schemaInferenceService
    ) {
        this.jsonPathSupport = jsonPathSupport;
        this.transformPipeline = transformPipeline;
        this.objectMapper = objectMapper;
        this.schemaInferenceService = schemaInferenceService;
    }

    public List<Map<String, Object>> previewTransform(String responseBody, JsonNode transformNode, int limit) {
        RuntimeConnectorConfig.TransformSettings transform = RuntimeConfigParser.parseTransformOnly(transformNode);
        List<Object> records = jsonPathSupport.readRecords(responseBody, transform.inputRoot());
        if (records.isEmpty()) {
            return List.of();
        }
        int end = Math.min(limit, records.size());
        return transformPipeline.transform(records.subList(0, end), transform);
    }

    public List<Map<String, Object>> suggestPaths(String responseBody, int limit) {
        return jsonPathSupport.suggestPaths(responseBody, limit);
    }

    public List<FieldMappingDto> inferMappings(String responseBody, String inputRoot, JsonNode recordSchema) {
        return schemaInferenceService.inferMappings(responseBody, inputRoot, recordSchema);
    }

    public String generateSample(JsonNode recordSchema, String mode, JsonNode realSample) {
        return schemaInferenceService.generateSampleResponse(recordSchema, mode, realSample);
    }

    public JsonNode inferSchema(String responseBody, String sampleJson, String inputRoot) {
        if (responseBody != null && !responseBody.isBlank()) {
            return schemaInferenceService.inferSchemaFromResponse(responseBody, inputRoot);
        }
        return schemaInferenceService.inferSchemaFromSample(sampleJson);
    }
}
