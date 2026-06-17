package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.pcitech.http.ingestion.core.dto.OpenApiOperationDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseRequestDto;
import com.pcitech.http.ingestion.core.dto.OpenApiParseResultDto;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OpenApiImportService {

    private static final int MAX_SPEC_BYTES = 16 * 1024 * 1024;
    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete", "head", "options");
    private static final List<String> SUCCESS_CODES = List.of("200", "201", "202", "204", "default");

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final WebClient webClient;

    public OpenApiImportService(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.jsonMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.webClient = webClientBuilder
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codec -> codec.defaultCodecs().maxInMemorySize(MAX_SPEC_BYTES))
                        .build())
                .build();
    }

    public OpenApiParseResultDto parse(OpenApiParseRequestDto request) {
        String specText = request.spec();
        if (specText == null || specText.isBlank()) {
            if (request.specUrl() == null || request.specUrl().isBlank()) {
                throw new IllegalArgumentException("spec or specUrl is required");
            }
            specText = fetchSpec(request.specUrl());
        }
        JsonNode root = parseRoot(specText.trim());
        List<String> serverUrls = readServerUrls(root);
        String defaultServer = serverUrls.isEmpty() ? "" : serverUrls.getFirst();
        List<OpenApiOperationDto> operations = extractOperations(root, defaultServer);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("No HTTP operations found in OpenAPI document");
        }
        return new OpenApiParseResultDto(serverUrls, operations);
    }

    private String fetchSpec(String specUrl) {
        return webClient.get()
                .uri(URI.create(specUrl))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .blockOptional()
                .orElseThrow(() -> new IllegalArgumentException("Failed to fetch OpenAPI document: " + specUrl));
    }

    private JsonNode parseRoot(String specText) {
        try {
            if (specText.startsWith("{")) {
                return jsonMapper.readTree(specText);
            }
            return yamlMapper.readTree(specText);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid OpenAPI document: " + ex.getMessage(), ex);
        }
    }

    private List<String> readServerUrls(JsonNode root) {
        List<String> urls = new ArrayList<>();
        JsonNode servers = root.path("servers");
        if (servers.isArray()) {
            for (JsonNode server : servers) {
                String url = server.path("url").asText(null);
                if (url != null && !url.isBlank()) {
                    urls.add(trimTrailingSlash(url));
                }
            }
        }
        if (urls.isEmpty() && root.has("host")) {
            String scheme = root.path("schemes").isArray() && !root.path("schemes").isEmpty()
                    ? root.path("schemes").get(0).asText("http")
                    : "http";
            String host = root.path("host").asText("");
            String basePath = root.path("basePath").asText("");
            if (!host.isBlank()) {
                urls.add(trimTrailingSlash(scheme + "://" + host + basePath));
            }
        }
        return urls;
    }

    private List<OpenApiOperationDto> extractOperations(JsonNode root, String defaultServer) {
        List<OpenApiOperationDto> operations = new ArrayList<>();
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            return operations;
        }
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            for (String method : HTTP_METHODS) {
                JsonNode operation = pathItem.path(method);
                if (operation.isMissingNode() || operation.isNull()) {
                    continue;
                }
                operations.add(buildOperation(root, defaultServer, method, path, pathItem, operation));
            }
        }
        return operations;
    }

    private OpenApiOperationDto buildOperation(
            JsonNode root,
            String defaultServer,
            String method,
            String path,
            JsonNode pathItem,
            JsonNode operation
    ) {
        String operationId = operation.path("operationId").asText(method.toUpperCase(Locale.ROOT) + " " + path);
        String summary = operation.path("summary").asText(operation.path("description").asText(operationId));
        JsonNode requestSchema = buildRequestSchema(root, pathItem, operation);
        JsonNode responseSchemaNode = resolveResponseSchema(root, operation);
        String inputRoot = inferInputRoot(responseSchemaNode);
        JsonNode responseSchema = toRecordResponseSchema(responseSchemaNode, inputRoot);
        JsonNode httpConfig = buildHttpConfig(root, defaultServer, method, path, operation, requestSchema);
        JsonNode suggestedPagination = OpenApiPaginationInference.infer(requestSchema, responseSchema, method);
        return new OpenApiOperationDto(
                operationId,
                method.toUpperCase(Locale.ROOT),
                path,
                summary,
                defaultServer,
                inputRoot,
                requestSchema,
                responseSchema,
                httpConfig,
                suggestedPagination
        );
    }

    private JsonNode buildRequestSchema(JsonNode root, JsonNode pathItem, JsonNode operation) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        ObjectNode bodyProperties = jsonMapper.createObjectNode();
        JsonNode parameters = collectParameters(pathItem, operation);
        if (parameters.isArray()) {
            for (JsonNode parameter : parameters) {
                String location = parameter.path("in").asText("");
                String name = parameter.path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                if ("body".equals(location)) {
                    JsonNode schema = resolveSchema(root, parameterSchemaNode(parameter), new HashSet<>());
                    if (schema.path("properties").isObject()) {
                        schema.path("properties").fields()
                                .forEachRemaining(entry -> bodyProperties.set(entry.getKey(), entry.getValue()));
                    }
                    continue;
                }
                Map<String, Object> field = parameterField(root, parameter);
                if ("header".equals(location)) {
                    headers.put(name, field);
                } else if ("query".equals(location)) {
                    query.put(name, field);
                } else if ("path".equals(location)) {
                    query.put(name, field);
                }
            }
        }
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("type", "object");
        JsonNode requestBody = operation.path("requestBody");
        if (!requestBody.isMissingNode() && !requestBody.isNull()) {
            JsonNode content = requestBody.path("content");
            JsonNode jsonContent = content.path("application/json");
            if (jsonContent.isMissingNode()) {
                jsonContent = firstContentNode(content);
            }
            if (!jsonContent.isMissingNode()) {
                JsonNode schema = resolveSchema(root, jsonContent.path("schema"), new HashSet<>());
                if (schema.path("properties").isObject()) {
                    schema.path("properties").fields()
                            .forEachRemaining(entry -> bodyProperties.set(entry.getKey(), entry.getValue()));
                }
            }
        }
        body.set("properties", bodyProperties);
        ObjectNode result = jsonMapper.createObjectNode();
        result.set("headers", objectProperties(headers));
        result.set("query", objectProperties(query));
        result.set("body", body);
        return result;
    }

    private JsonNode buildHttpConfig(
            JsonNode root,
            String serverUrl,
            String method,
            String path,
            JsonNode operation,
            JsonNode requestSchema
    ) {
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> query = new LinkedHashMap<>();
        JsonNode headerProps = requestSchema.path("headers").path("properties");
        if (headerProps.isObject()) {
            headerProps.fields().forEachRemaining(entry ->
                    headers.put(entry.getKey(), exampleAsString(entry.getValue())));
        }
        JsonNode queryProps = requestSchema.path("query").path("properties");
        if (queryProps.isObject()) {
            queryProps.fields().forEachRemaining(entry ->
                    query.put(entry.getKey(), exampleAsString(entry.getValue())));
        }

        String bodyType = "none";
        JsonNode bodyNode = null;
        JsonNode bodyProps = requestSchema.path("body").path("properties");
        if (bodyProps.isObject() && !bodyProps.isEmpty()) {
            bodyType = "json";
            bodyNode = exampleObjectFromProperties(bodyProps);
        }

        ObjectNode http = jsonMapper.createObjectNode();
        http.put("method", method.toUpperCase(Locale.ROOT));
        http.put("url", joinUrl(serverUrl, path));
        http.set("headers", jsonMapper.valueToTree(headers));
        http.set("query", jsonMapper.valueToTree(query));
        http.put("body_type", bodyType);
        if ("json".equals(bodyType)) {
            http.set("body", bodyNode);
        } else {
            http.putNull("body");
        }
        http.set("form", jsonMapper.createObjectNode());
        http.put("timeout_ms", 30000);
        return http;
    }

    private JsonNode resolveResponseSchema(JsonNode root, JsonNode operation) {
        JsonNode responses = operation.path("responses");
        if (!responses.isObject()) {
            return jsonMapper.createObjectNode();
        }
        for (String code : SUCCESS_CODES) {
            JsonNode response = responses.path(code);
            if (response.isMissingNode() || response.isNull()) {
                continue;
            }
            JsonNode directSchema = response.path("schema");
            if (!directSchema.isMissingNode() && !directSchema.isNull()) {
                return resolveSchema(root, directSchema, new HashSet<>());
            }
            JsonNode content = response.path("content");
            JsonNode jsonContent = content.path("application/json");
            if (jsonContent.isMissingNode()) {
                jsonContent = firstContentNode(content);
            }
            if (!jsonContent.isMissingNode()) {
                return resolveSchema(root, jsonContent.path("schema"), new HashSet<>());
            }
        }
        return jsonMapper.createObjectNode();
    }

    private JsonNode toRecordResponseSchema(JsonNode schema, String inputRoot) {
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("input_root", inputRoot);
        if ("$".equals(inputRoot)) {
            if ("array".equals(schema.path("type").asText())) {
                result.set("record", itemSchema(schema));
                result.set("envelope", jsonMapper.createObjectNode());
            } else {
                result.set("record", objectSchema(schema.path("properties")));
                result.set("envelope", jsonMapper.createObjectNode());
            }
            return result;
        }
        String arrayField = inputRoot.startsWith("$.") ? inputRoot.substring(2) : inputRoot;
        JsonNode properties = schema.path("properties");
        JsonNode recordItems = properties.path(arrayField).path("items");
        ObjectNode envelope = jsonMapper.createObjectNode();
        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                if (!entry.getKey().equals(arrayField)) {
                    envelope.set(entry.getKey(), simplifiedProperty(entry.getValue()));
                }
            });
        }
        result.set("record", itemSchema(recordItems.isMissingNode() ? properties.path(arrayField) : recordItems));
        result.set("envelope", envelope);
        return result;
    }

    private String inferInputRoot(JsonNode schema) {
        if ("array".equals(schema.path("type").asText())) {
            return "$";
        }
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if ("array".equals(entry.getValue().path("type").asText())) {
                    return "$." + entry.getKey();
                }
            }
        }
        return "$";
    }

    private JsonNode resolveSchema(JsonNode root, JsonNode schema, Set<String> resolving) {
        if (schema == null || schema.isNull() || schema.isMissingNode()) {
            return jsonMapper.createObjectNode();
        }
        if (schema.has("$ref")) {
            String ref = schema.path("$ref").asText();
            if (!resolving.add(ref)) {
                return jsonMapper.createObjectNode();
            }
            JsonNode resolved = resolveRef(root, ref);
            JsonNode merged = resolveSchema(root, resolved, resolving);
            resolving.remove(ref);
            return merged;
        }
        if (schema.has("allOf") && schema.path("allOf").isArray()) {
            ObjectNode merged = jsonMapper.createObjectNode();
            merged.put("type", "object");
            ObjectNode properties = jsonMapper.createObjectNode();
            for (JsonNode part : schema.path("allOf")) {
                JsonNode resolved = resolveSchema(root, part, resolving);
                if (resolved.path("properties").isObject()) {
                    resolved.path("properties").fields().forEachRemaining(entry -> properties.set(entry.getKey(), entry.getValue()));
                }
            }
            merged.set("properties", properties);
            return merged;
        }
        ObjectNode copy = schema.deepCopy();
        if (copy.path("properties").isObject()) {
            ObjectNode properties = jsonMapper.createObjectNode();
            copy.path("properties").fields().forEachRemaining(entry ->
                    properties.set(entry.getKey(), resolveSchema(root, entry.getValue(), resolving)));
            copy.set("properties", properties);
        }
        if (copy.path("items").isObject()) {
            copy.set("items", resolveSchema(root, copy.path("items"), resolving));
        }
        return copy;
    }

    private JsonNode resolveRef(JsonNode root, String ref) {
        if (!ref.startsWith("#/")) {
            throw new IllegalArgumentException("Unsupported schema ref: " + ref);
        }
        String path = ref.substring(2);
        if (path.startsWith("definitions/")) {
            return root.path("definitions").path(path.substring("definitions/".length()));
        }
        JsonNode node = root;
        for (String segment : path.split("/")) {
            node = node.path(segment);
        }
        return node;
    }

    private JsonNode collectParameters(JsonNode pathItem, JsonNode operation) {
        ArrayNode merged = jsonMapper.createArrayNode();
        appendParameters(merged, pathItem.path("parameters"));
        appendParameters(merged, operation.path("parameters"));
        return merged;
    }

    private void appendParameters(ArrayNode target, JsonNode parameters) {
        if (!parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            target.add(parameter);
        }
    }

    private JsonNode parameterSchemaNode(JsonNode parameter) {
        if (parameter.has("schema") && !parameter.path("schema").isNull() && !parameter.path("schema").isMissingNode()) {
            return parameter.path("schema");
        }
        if (parameter.has("type")) {
            return parameter;
        }
        return jsonMapper.createObjectNode();
    }

    private Map<String, Object> parameterField(JsonNode root, JsonNode parameter) {
        Map<String, Object> field = new LinkedHashMap<>();
        JsonNode schema = resolveSchema(root, parameterSchemaNode(parameter), new HashSet<>());
        String type = schema.path("type").asText(null);
        if (type == null || type.isBlank()) {
            type = parameter.path("type").asText("string");
        }
        field.put("type", normalizeType(type));
        field.put("in", parameter.path("in").asText(""));
        if (parameter.has("description")) {
            field.put("description", parameter.path("description").asText());
        }
        if (schema.has("example")) {
            field.put("example", schema.path("example"));
        } else if (parameter.has("example")) {
            field.put("example", parameter.path("example"));
        } else {
            field.put("example", defaultExample(field.get("type").toString()));
        }
        return field;
    }

    private ObjectNode objectProperties(Map<String, Object> properties) {
        ObjectNode object = jsonMapper.createObjectNode();
        object.put("type", "object");
        object.set("properties", jsonMapper.valueToTree(properties));
        return object;
    }

    private JsonNode itemSchema(JsonNode schema) {
        if ("array".equals(schema.path("type").asText())) {
            return objectSchema(schema.path("items").path("properties"));
        }
        return objectSchema(schema.path("properties"));
    }

    private ObjectNode objectSchema(JsonNode properties) {
        ObjectNode record = jsonMapper.createObjectNode();
        record.put("type", "object");
        ObjectNode props = jsonMapper.createObjectNode();
        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> props.set(entry.getKey(), simplifiedProperty(entry.getValue())));
        }
        record.set("properties", props);
        return record;
    }

    private JsonNode simplifiedProperty(JsonNode schema) {
        ObjectNode property = jsonMapper.createObjectNode();
        String type = schema.path("type").asText("string");
        if ("array".equals(type)) {
            property.put("type", "array");
            property.set("items", simplifiedProperty(schema.path("items")));
        } else if ("object".equals(type)) {
            property.put("type", "object");
            property.set("properties", schema.path("properties").isObject() ? schema.path("properties") : jsonMapper.createObjectNode());
        } else {
            property.put("type", normalizeType(type));
            if (schema.has("example")) {
                property.set("example", schema.path("example"));
            } else {
                setJsonValue(property, "example", defaultExample(type));
            }
        }
        if (schema.has("description")) {
            property.put("description", schema.path("description").asText());
        }
        return property;
    }

    private JsonNode exampleObjectFromProperties(JsonNode properties) {
        ObjectNode body = jsonMapper.createObjectNode();
        if (!properties.isObject()) {
            return body;
        }
        properties.fields().forEachRemaining(entry -> {
            JsonNode property = entry.getValue();
            if ("object".equals(property.path("type").asText())) {
                body.set(entry.getKey(), exampleObjectFromProperties(property.path("properties")));
            } else if ("array".equals(property.path("type").asText())) {
                ArrayNode array = jsonMapper.createArrayNode();
                array.add(jsonMapper.valueToTree(defaultExample(property.path("items").path("type").asText("string"))));
                body.set(entry.getKey(), array);
            } else if (property.has("example")) {
                body.set(entry.getKey(), property.path("example"));
            } else {
                setJsonValue(body, entry.getKey(), defaultExample(property.path("type").asText("string")));
            }
        });
        return body;
    }

    private JsonNode firstContentNode(JsonNode content) {
        if (!content.isObject()) {
            return jsonMapper.missingNode();
        }
        Iterator<JsonNode> values = content.elements();
        return values.hasNext() ? values.next() : jsonMapper.missingNode();
    }

    private String exampleAsString(JsonNode property) {
        if (property.has("example")) {
            JsonNode example = property.path("example");
            return example.isValueNode() ? example.asText() : example.toString();
        }
        return String.valueOf(defaultExample(property.path("type").asText("string")));
    }

    private Object defaultExample(String type) {
        return switch (normalizeType(type)) {
            case "integer", "long" -> 1;
            case "number", "double" -> 1.0;
            case "boolean" -> false;
            default -> "example";
        };
    }

    private void setJsonValue(ObjectNode node, String key, Object value) {
        node.set(key, jsonMapper.valueToTree(value));
    }

    private String normalizeType(String type) {
        return switch (type) {
            case "int32", "int64" -> "integer";
            case "float", "double" -> "number";
            default -> type == null || type.isBlank() ? "string" : type;
        };
    }

    private String joinUrl(String serverUrl, String path) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return path;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = trimTrailingSlash(serverUrl);
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
