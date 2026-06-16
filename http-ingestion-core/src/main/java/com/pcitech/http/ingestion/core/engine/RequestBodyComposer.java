package com.pcitech.http.ingestion.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.WatermarkState;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class RequestBodyComposer {

    private static final DateTimeFormatter DAHUA_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MEIYA_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private RequestBodyComposer() {
    }

    public static String compose(
            ObjectMapper mapper,
            String bodyTemplate,
            RuntimeConnectorConfig.PaginationSettings pagination,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            int page,
            WatermarkState watermark,
            boolean incrementalMode
    ) {
        try {
            ObjectNode root = parseBody(mapper, bodyTemplate);
            if ("body".equalsIgnoreCase(pagination.location())) {
                applyBodyPagination(root, pagination, page);
            }
            if (incrementalMode && incremental != null && incremental.enabled()
                    && "body".equalsIgnoreCase(incremental.requestTarget())) {
                applyBodyIncremental(root, incremental, watermark);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to compose HTTP request body: " + ex.getMessage(), ex);
        }
    }

    public static String composeWithCursor(
            ObjectMapper mapper,
            String bodyTemplate,
            RuntimeConnectorConfig.PaginationSettings pagination,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            String cursor,
            boolean firstPage,
            WatermarkState watermark,
            boolean incrementalMode
    ) {
        try {
            ObjectNode root = parseBody(mapper, bodyTemplate);
            if ("body".equalsIgnoreCase(pagination.location())) {
                applyBodyCursor(root, pagination, cursor, firstPage);
            }
            if (incrementalMode && incremental != null && incremental.enabled()
                    && "body".equalsIgnoreCase(incremental.requestTarget())) {
                applyBodyIncremental(root, incremental, watermark);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to compose HTTP request body: " + ex.getMessage(), ex);
        }
    }

    private static ObjectNode parseBody(ObjectMapper mapper, String bodyTemplate) throws java.io.IOException {
        if (bodyTemplate == null || bodyTemplate.isBlank()) {
            return mapper.createObjectNode();
        }
        JsonNode node = mapper.readTree(bodyTemplate);
        if (node instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        throw new IllegalArgumentException("HTTP body must be a JSON object");
    }

    private static void applyBodyPagination(
            ObjectNode root,
            RuntimeConnectorConfig.PaginationSettings pagination,
            int page
    ) {
        int pageSize = pagination.pageSize();
        if ("skip_limit".equalsIgnoreCase(pagination.pageValueType())
                || "offset_limit".equalsIgnoreCase(pagination.strategy())) {
            long skip = (long) (page - pagination.pageStart()) * pageSize;
            setByPath(root, pagination.pageParam(), JsonNodeFactory.instance.numberNode(skip));
            setByPath(root, pagination.pageSizeParam(), JsonNodeFactory.instance.numberNode(pageSize));
            return;
        }
        setByPath(root, pagination.pageParam(), JsonNodeFactory.instance.numberNode(page));
        setByPath(root, pagination.pageSizeParam(), JsonNodeFactory.instance.numberNode(pageSize));
    }

    private static void applyBodyCursor(
            ObjectNode root,
            RuntimeConnectorConfig.PaginationSettings pagination,
            String cursor,
            boolean firstPage
    ) {
        if (pagination.pageSizeParam() != null && !pagination.pageSizeParam().isBlank()) {
            setByPath(root, pagination.pageSizeParam(), JsonNodeFactory.instance.numberNode(pagination.pageSize()));
        }
        if (firstPage && pagination.firstPageOmitCursor()) {
            return;
        }
        if (pagination.cursorParam() == null || pagination.cursorParam().isBlank()) {
            throw new IllegalArgumentException("pagination.cursor_param is required for cursor strategy");
        }
        setByPath(root, pagination.cursorParam(), TextNode.valueOf(cursor == null ? "" : cursor));
    }

    private static void applyBodyIncremental(
            ObjectNode root,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            WatermarkState watermark
    ) {
        if (incremental.isMonotonicId()) {
            String path = incremental.requestBodyPath() != null && !incremental.requestBodyPath().isBlank()
                    ? incremental.requestBodyPath()
                    : incremental.requestParam();
            if (!watermark.hasLastId() || path == null || path.isBlank()) {
                return;
            }
            try {
                long id = Long.parseLong(watermark.lastId());
                setByPath(root, path, JsonNodeFactory.instance.numberNode(id));
            } catch (NumberFormatException ex) {
                setByPath(root, path, TextNode.valueOf(watermark.lastId()));
            }
            return;
        }
        if (!watermark.hasTimestamp() || incremental.requestBodyPath() == null || incremental.requestBodyPath().isBlank()) {
            return;
        }
        Instant effective = watermark.timestamp().minus(parseOverlap(incremental.overlap()));
        Instant end = Instant.now().plus(1, ChronoUnit.MINUTES);
        if (incremental.requestBodyEndPath() != null && !incremental.requestBodyEndPath().isBlank()) {
            setByPath(root, incremental.requestBodyPath(), textNode(formatTime(effective, incremental.timeFormat())));
            setByPath(root, incremental.requestBodyEndPath(), textNode(formatTime(end, incremental.timeFormat())));
            return;
        }
        ArrayNode range = root.arrayNode();
        range.add(formatTime(effective, incremental.timeFormat()));
        range.add(formatTime(end, incremental.timeFormat()));
        setByPath(root, incremental.requestBodyPath(), range);
    }

    private static TextNode textNode(String value) {
        return TextNode.valueOf(value);
    }

    static void setByPath(ObjectNode root, String dotPath, JsonNode value) {
        String[] parts = dotPath.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode child = current.get(parts[i]);
            ObjectNode objectChild;
            if (child instanceof ObjectNode existing) {
                objectChild = existing;
            } else {
                objectChild = current.putObject(parts[i]);
            }
            current = objectChild;
        }
        current.set(parts[parts.length - 1], value);
    }

    static String formatTime(Instant instant, String format) {
        if (instant == null) {
            return null;
        }
        return switch (format == null ? "iso_instant" : format) {
            case "dahua_utc" -> DAHUA_UTC.format(instant);
            case "meiya_datetime" -> MEIYA_DATETIME.format(instant);
            default -> instant.toString();
        };
    }

    private static java.time.Duration parseOverlap(String overlap) {
        if (overlap == null || overlap.isBlank()) {
            return java.time.Duration.ZERO;
        }
        String value = overlap.trim().toLowerCase();
        if (value.endsWith("m")) {
            return java.time.Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("h")) {
            return java.time.Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("s")) {
            return java.time.Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return java.time.Duration.ofMinutes(5);
    }
}
