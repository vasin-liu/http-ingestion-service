package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcitech.http.ingestion.core.dto.FieldMappingDto;
import com.pcitech.http.ingestion.core.engine.JsonPathSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SchemaInferenceService {

    private final JsonPathSupport jsonPathSupport;
    private final ObjectMapper objectMapper;

    public SchemaInferenceService(JsonPathSupport jsonPathSupport, ObjectMapper objectMapper) {
        this.jsonPathSupport = jsonPathSupport;
        this.objectMapper = objectMapper;
    }

    public List<FieldMappingDto> inferMappings(String responseBody, String inputRoot, JsonNode recordSchema) {
        if (recordSchema != null && !recordSchema.isNull()) {
            return inferFromRecordSchema(recordSchema);
        }
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        String root = inputRoot == null || inputRoot.isBlank() ? "$" : inputRoot;
        List<Object> records = jsonPathSupport.readRecords(responseBody, root);
        if (records.isEmpty()) {
            return List.of();
        }
        Object first = records.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<FieldMappingDto> mappings = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String field = String.valueOf(entry.getKey());
            mappings.add(new FieldMappingDto(field, "$." + field, inferTypeFromValue(entry.getValue())));
        }
        return mappings;
    }

    public JsonNode inferSchemaFromResponse(String responseBody, String inputRoot) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("responseBody is required");
        }
        String root = inputRoot == null || inputRoot.isBlank() ? "$" : inputRoot;
        List<Object> records = jsonPathSupport.readRecords(responseBody, root);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No records found at input_root: " + root);
        }
        Object first = records.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Records at input_root are not objects");
        }
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("input_root", root);
        ObjectNode record = objectMapper.createObjectNode();
        record.put("type", "object");
        record.set("properties", propertiesFromMap(map));
        schema.set("record", record);
        schema.set("envelope", envelopeFromResponse(responseBody, root));
        return schema;
    }

    public JsonNode inferSchemaFromSample(String sampleJson) {
        if (sampleJson == null || sampleJson.isBlank()) {
            throw new IllegalArgumentException("sampleJson is required");
        }
        try {
            JsonNode rootNode = objectMapper.readTree(sampleJson);
            DetectedArray detected = detectRecordArray(rootNode, "$");
            if (detected == null) {
                if (rootNode.isObject()) {
                    ObjectNode schema = objectMapper.createObjectNode();
                    schema.put("input_root", "$");
                    ObjectNode record = objectMapper.createObjectNode();
                    record.put("type", "object");
                    record.set("properties", propertiesFromJson(rootNode));
                    schema.set("record", record);
                    schema.set("envelope", objectMapper.createObjectNode());
                    return schema;
                }
                throw new IllegalArgumentException("Unable to detect record array in sample JSON");
            }
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("input_root", detected.path());
            ObjectNode record = objectMapper.createObjectNode();
            record.put("type", "object");
            record.set("properties", propertiesFromJson(detected.firstItem()));
            schema.set("record", record);
            if (detected.path().startsWith("$.") && detected.path().contains(".")) {
                schema.set("envelope", envelopeFromParent(rootNode, detected.path()));
            } else {
                schema.set("envelope", envelopeFromResponse(sampleJson, detected.path()));
            }
            return schema;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid sample JSON", ex);
        }
    }

    public String generateSampleResponse(JsonNode recordSchema, String mode, JsonNode realSample) {
        if ("real".equalsIgnoreCase(mode) && realSample != null && !realSample.isNull()) {
            try {
                return objectMapper.writeValueAsString(realSample);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid real sample JSON", ex);
            }
        }
        if (recordSchema == null || recordSchema.isNull()) {
            throw new IllegalArgumentException("recordSchema is required");
        }
        JsonNode recordNode = recordSchema.path("record");
        if (recordNode.isMissingNode() || recordNode.isNull()) {
            recordNode = recordSchema;
        }
        ObjectNode record = generateRecordObject(recordNode, false);
        ObjectNode envelope = objectMapper.createObjectNode();
        JsonNode envelopeSchema = recordSchema.path("envelope");
        if (envelopeSchema.isObject()) {
            envelopeSchema.fields().forEachRemaining(entry ->
                    envelope.set(entry.getKey(), toJsonNode(generatePropertyValue(entry.getValue(), false)))
            );
        }
        String inputRoot = recordSchema.path("input_root").asText("$.results");
        ObjectNode response = objectMapper.createObjectNode();
        envelope.fields().forEachRemaining(entry -> response.set(entry.getKey(), entry.getValue()));
        if (inputRoot.endsWith("[*]")) {
            String arrayField = inputRoot.substring(2, inputRoot.length() - 3);
            ArrayNode items = objectMapper.createArrayNode();
            items.add(record);
            response.set(arrayField, items);
        } else if (inputRoot.startsWith("$.") && !inputRoot.contains("[*]")) {
            String field = inputRoot.substring(2);
            ArrayNode items = objectMapper.createArrayNode();
            items.add(record);
            response.set(field, items);
        } else {
            response.set("data", objectMapper.createArrayNode().add(record));
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize generated sample", ex);
        }
    }

    private List<FieldMappingDto> inferFromRecordSchema(JsonNode recordSchema) {
        JsonNode properties = recordSchema.path("record").path("properties");
        if (!properties.isObject()) {
            properties = recordSchema.path("properties");
        }
        if (!properties.isObject()) {
            return List.of();
        }
        List<FieldMappingDto> mappings = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String field = entry.getKey();
            mappings.add(new FieldMappingDto(field, "$." + field, mapSchemaType(entry.getValue())));
        }
        return mappings;
    }

    private ObjectNode generateRecordObject(JsonNode recordSchema, boolean randomize) {
        ObjectNode record = objectMapper.createObjectNode();
        JsonNode properties = recordSchema.path("properties");
        if (!properties.isObject()) {
            return record;
        }
        properties.fields().forEachRemaining(entry ->
                record.set(entry.getKey(), toJsonNode(generatePropertyValue(entry.getValue(), randomize)))
        );
        return record;
    }

    private Object generatePropertyValue(JsonNode propertySchema, boolean randomize) {
        if (propertySchema == null || propertySchema.isNull()) {
            return "";
        }
        if (!randomize && propertySchema.hasNonNull("example")) {
            JsonNode example = propertySchema.get("example");
            if (example.isNumber()) {
                return example.numberValue();
            }
            if (example.isBoolean()) {
                return example.booleanValue();
            }
            return example.asText();
        }
        String type = propertySchema.path("type").asText("string");
        return switch (type) {
            case "integer", "long" -> ThreadLocalRandom.current().nextInt(1, 10_000);
            case "number", "double", "decimal" -> ThreadLocalRandom.current().nextDouble(100);
            case "boolean" -> ThreadLocalRandom.current().nextBoolean();
            default -> propertySchema.path("example").asText("sample-" + ThreadLocalRandom.current().nextInt(1000));
        };
    }

    private JsonNode toJsonNode(Object value) {
        return objectMapper.valueToTree(value);
    }

    private String inferTypeFromValue(Object value) {
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "long";
        }
        if (value instanceof Float || value instanceof Double) {
            return "double";
        }
        return "string";
    }

    private String mapSchemaType(JsonNode propertySchema) {
        String type = propertySchema.path("type").asText("string");
        return switch (type) {
            case "integer", "long" -> "long";
            case "number", "double" -> "double";
            case "boolean" -> "boolean";
            case "decimal" -> "decimal";
            default -> "string";
        };
    }

    private ObjectNode propertiesFromMap(Map<?, ?> map) {
        ObjectNode properties = objectMapper.createObjectNode();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            properties.set(String.valueOf(entry.getKey()), propertySchemaFromValue(entry.getValue()));
        }
        return properties;
    }

    private ObjectNode propertiesFromJson(JsonNode node) {
        ObjectNode properties = objectMapper.createObjectNode();
        if (!node.isObject()) {
            return properties;
        }
        node.fields().forEachRemaining(entry ->
                properties.set(entry.getKey(), propertySchemaFromValue(entry.getValue()))
        );
        return properties;
    }

    private ObjectNode propertySchemaFromValue(Object value) {
        ObjectNode property = objectMapper.createObjectNode();
        if (value instanceof Boolean) {
            property.put("type", "boolean");
            property.put("example", (Boolean) value);
        } else if (value instanceof Integer || value instanceof Long) {
            property.put("type", "long");
            property.put("example", ((Number) value).longValue());
        } else if (value instanceof Float || value instanceof Double) {
            property.put("type", "double");
            property.put("example", ((Number) value).doubleValue());
        } else if (value instanceof Map<?, ?> || value instanceof JsonNode json && json.isObject()) {
            property.put("type", "object");
            if (value instanceof Map<?, ?> map) {
                property.set("properties", propertiesFromMap(map));
            } else {
                property.set("properties", propertiesFromJson((JsonNode) value));
            }
        } else {
            property.put("type", "string");
            property.put("example", String.valueOf(value));
        }
        return property;
    }

    private ObjectNode propertySchemaFromValue(JsonNode value) {
        ObjectNode property = objectMapper.createObjectNode();
        if (value.isBoolean()) {
            property.put("type", "boolean");
            property.put("example", value.booleanValue());
        } else if (value.isIntegralNumber()) {
            property.put("type", "long");
            property.put("example", value.longValue());
        } else if (value.isNumber()) {
            property.put("type", "double");
            property.put("example", value.doubleValue());
        } else if (value.isObject()) {
            property.put("type", "object");
            property.set("properties", propertiesFromJson(value));
        } else {
            property.put("type", "string");
            property.put("example", value.asText());
        }
        return property;
    }

    private ObjectNode envelopeFromResponse(String responseBody, String inputRoot) {
        ObjectNode envelope = objectMapper.createObjectNode();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isObject()) {
                return envelope;
            }
            String arrayField = arrayFieldFromInputRoot(inputRoot);
            root.fields().forEachRemaining(entry -> {
                if (arrayField != null && arrayField.equals(entry.getKey())) {
                    return;
                }
                envelope.set(entry.getKey(), propertySchemaFromValue(entry.getValue()));
            });
        } catch (Exception ignored) {
            // keep empty envelope
        }
        return envelope;
    }

    private ObjectNode envelopeFromParent(JsonNode root, String inputRoot) {
        ObjectNode envelope = objectMapper.createObjectNode();
        if (!root.isObject() || !inputRoot.startsWith("$.")) {
            return envelope;
        }
        String[] segments = inputRoot.substring(2).split("\\.");
        JsonNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            current = current.path(segments[i]);
        }
        if (current.isObject()) {
            String leaf = segments[segments.length - 1];
            current.fields().forEachRemaining(entry -> {
                if (leaf.equals(entry.getKey())) {
                    return;
                }
                envelope.set(entry.getKey(), propertySchemaFromValue(entry.getValue()));
            });
        }
        return envelope;
    }

    private String arrayFieldFromInputRoot(String inputRoot) {
        if (inputRoot == null || inputRoot.isBlank() || "$".equals(inputRoot)) {
            return null;
        }
        String path = inputRoot.startsWith("$.") ? inputRoot.substring(2) : inputRoot;
        int bracket = path.indexOf('[');
        if (bracket >= 0) {
            path = path.substring(0, bracket);
        }
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private DetectedArray detectRecordArray(JsonNode node, String currentPath) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray() && !node.isEmpty() && node.get(0).isObject()) {
            String path = currentPath;
            if (path.endsWith("[*]")) {
                path = path.substring(0, path.length() - 3);
            }
            return new DetectedArray(path, node.get(0));
        }
        if (!node.isObject()) {
            return null;
        }
        List<String> preferred = List.of("results", "data", "items", "records", "list");
        for (String key : preferred) {
            JsonNode child = node.get(key);
            DetectedArray detected = detectRecordArray(child, childPath(currentPath, key));
            if (detected != null) {
                return detected;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            DetectedArray detected = detectRecordArray(entry.getValue(), childPath(currentPath, entry.getKey()));
            if (detected != null) {
                return detected;
            }
        }
        return null;
    }

    private String childPath(String currentPath, String key) {
        if ("$".equals(currentPath)) {
            return "$." + key;
        }
        return currentPath + "." + key;
    }

    private record DetectedArray(String path, JsonNode firstItem) {
    }
}
