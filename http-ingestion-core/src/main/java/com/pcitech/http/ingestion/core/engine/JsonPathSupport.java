package com.pcitech.http.ingestion.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.dto.TrialRequestDto;
import com.pcitech.http.ingestion.core.dto.TrialResponseDto;
import com.pcitech.http.ingestion.core.service.TrialRequestService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JsonPathSupport {

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build();

    private final ObjectMapper objectMapper;

    public JsonPathSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Object> readRecords(String body, String inputRoot) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
        Object value = context.read(inputRoot == null || inputRoot.isBlank() ? "$" : inputRoot);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(value);
    }

    public Integer readInteger(String body, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
        Object value = context.read(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String readString(String body, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
        Object value = context.read(path);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    public Boolean readBoolean(String body, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
        Object value = context.read(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public Object readValue(Object record, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String json = toJson(record);
        DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(json);
        return context.read(path.startsWith("$") ? path : "$." + path);
    }

    public Instant readInstant(Object record, String path) {
        Object value = readValue(record, path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (DateTimeParseException ex) {
            try {
                return DAHUA_UTC.parse(String.valueOf(value), Instant::from);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return MEIYA_DATETIME.parse(String.valueOf(value), Instant::from);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static final DateTimeFormatter MEIYA_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAHUA_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    public List<Map<String, Object>> suggestPaths(String body, int maxPaths) {
        List<Map<String, Object>> paths = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return paths;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            collectPaths(root, "$", paths, maxPaths);
        } catch (Exception ignored) {
        }
        return paths;
    }

    private void collectPaths(JsonNode node, String currentPath, List<Map<String, Object>> paths, int maxPaths) {
        if (paths.size() >= maxPaths) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String childPath = currentPath.equals("$")
                        ? "$." + entry.getKey()
                        : currentPath + "." + entry.getKey();
                addPath(paths, childPath, entry.getValue(), maxPaths);
                collectPaths(entry.getValue(), childPath, paths, maxPaths);
            });
        } else if (node.isArray() && !node.isEmpty()) {
            String arrayPath = currentPath + "[*]";
            addPath(paths, arrayPath, node.get(0), maxPaths);
            collectPaths(node.get(0), currentPath + "[0]", paths, maxPaths);
        }
    }

    private void addPath(List<Map<String, Object>> paths, String path, JsonNode sample, int maxPaths) {
        if (paths.size() >= maxPaths) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", path);
        if (sample != null && !sample.isContainerNode()) {
            item.put("sample", sample.asText());
        }
        paths.add(item);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
