package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Infers connector pagination settings from OpenAPI request/response schemas.
 */
final class OpenApiPaginationInference {

    private static final Set<String> PAGE_NAMES = Set.of("page", "page_number", "pagenumber", "page_no", "pagenum");
    private static final Set<String> PAGE_SIZE_NAMES = Set.of(
            "page_size", "pagesize", "per_page", "perpage", "size", "limit"
    );
    private static final Set<String> OFFSET_NAMES = Set.of("offset", "skip");
    private static final Set<String> LIMIT_NAMES = Set.of("limit");
    private static final Set<String> CURSOR_NAMES = Set.of(
            "cursor", "page_token", "pagetoken", "next_token", "nexttoken", "after"
    );
    private static final Set<String> HAS_MORE_NAMES = Set.of("hasmore", "has_more");
    private static final Set<String> NEXT_CURSOR_NAMES = Set.of(
            "nextcursor", "next_cursor", "nextpagetoken", "next_page_token",
            "nexttoken", "next_token", "next"
    );
    private static final Set<String> TOTAL_NAMES = Set.of("total", "totalcount", "total_count", "count");

    private OpenApiPaginationInference() {
    }

    static JsonNode infer(JsonNode requestSchema, JsonNode responseSchema, String method) {
        if (method == null || !"GET".equalsIgnoreCase(method.trim())) {
            return null;
        }
        Map<String, String> queryParams = paramNames(requestSchema, "query");
        Map<String, String> bodyParams = paramNames(requestSchema, "body");
        JsonNode envelope = responseSchema != null ? responseSchema.path("envelope") : null;

        ObjectNode offsetLimit = inferOffsetLimit(queryParams);
        if (offsetLimit != null) {
            return enrichWithTotalCount(offsetLimit, envelope);
        }
        JsonNode cursor = inferCursor(queryParams, bodyParams, envelope);
        if (cursor != null) {
            return cursor;
        }
        ObjectNode pagePageSize = inferPagePageSize(queryParams);
        if (pagePageSize != null) {
            return enrichWithTotalCount(pagePageSize, envelope);
        }
        return null;
    }

    private static Map<String, String> paramNames(JsonNode requestSchema, String section) {
        Map<String, String> names = new HashMap<>();
        if (requestSchema == null || requestSchema.isMissingNode()) {
            return names;
        }
        JsonNode properties = requestSchema.path(section).path("properties");
        if (!properties.isObject()) {
            return names;
        }
        Iterator<String> fieldNames = properties.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            names.put(normalize(name), name);
        }
        return names;
    }

    private static ObjectNode inferOffsetLimit(Map<String, String> queryParams) {
        String offset = firstMatch(queryParams, OFFSET_NAMES);
        String limit = firstMatch(queryParams, LIMIT_NAMES);
        if (offset == null || limit == null) {
            return null;
        }
        ObjectNode pagination = defaults();
        pagination.put("strategy", "offset_limit");
        pagination.put("location", "query");
        pagination.put("page_value_type", "offset");
        pagination.put("page_param", offset);
        pagination.put("page_size_param", limit);
        pagination.put("page_start", 0);
        return pagination;
    }

    private static JsonNode inferCursor(
            Map<String, String> queryParams,
            Map<String, String> bodyParams,
            JsonNode envelope
    ) {
        String cursorInQuery = firstMatch(queryParams, CURSOR_NAMES);
        String cursorInBody = firstMatch(bodyParams, CURSOR_NAMES);
        String cursorParam = cursorInQuery != null ? cursorInQuery : cursorInBody;
        if (cursorParam == null) {
            return null;
        }
        ObjectNode pagination = defaults();
        pagination.put("strategy", "cursor");
        pagination.put("location", cursorInQuery != null ? "query" : "body");
        pagination.put("cursor_param", cursorParam);
        String cursorPath = inferCursorResponsePath(envelope);
        if (cursorPath != null) {
            pagination.put("cursor_response_path", cursorPath);
        }
        String hasMorePath = inferHasMorePath(envelope);
        if (hasMorePath != null) {
            pagination.put("has_more_path", hasMorePath);
        }
        return pagination;
    }

    private static ObjectNode inferPagePageSize(Map<String, String> queryParams) {
        String page = firstMatch(queryParams, PAGE_NAMES);
        if (page == null) {
            return null;
        }
        String pageSize = firstMatch(queryParams, PAGE_SIZE_NAMES);
        ObjectNode pagination = defaults();
        pagination.put("strategy", "page_page_size");
        pagination.put("location", "query");
        pagination.put("page_value_type", "page_number");
        pagination.put("page_param", page);
        pagination.put("page_size_param", pageSize != null ? pageSize : "page_size");
        pagination.put("page_start", 1);
        return pagination;
    }

    private static JsonNode enrichWithTotalCount(ObjectNode pagination, JsonNode envelope) {
        String totalPath = inferTotalCountPath(envelope);
        if (totalPath == null) {
            return pagination;
        }
        ObjectNode totalCount = pagination.putObject("total_count");
        totalCount.put("source", "json_path");
        totalCount.put("json_path", totalPath);
        return pagination;
    }

    private static String inferTotalCountPath(JsonNode envelope) {
        JsonNode fields = envelopeFields(envelope);
        if (fields == null) {
            return null;
        }
        String direct = propertyByNames(fields, TOTAL_NAMES);
        if (direct != null) {
            return "$." + direct;
        }
        for (String container : new String[]{"meta", "pagination", "page"}) {
            JsonNode nestedProps = nestedContainerProperties(fields, container);
            if (nestedProps == null) {
                continue;
            }
            String total = propertyByNames(nestedProps, TOTAL_NAMES);
            if (total != null) {
                return "$." + container + "." + total;
            }
        }
        return null;
    }

    private static String inferCursorResponsePath(JsonNode envelope) {
        JsonNode fields = envelopeFields(envelope);
        if (fields == null) {
            return null;
        }
        String direct = propertyByNames(fields, NEXT_CURSOR_NAMES);
        if (direct != null) {
            return "$." + direct;
        }
        for (String container : new String[]{"meta", "pagination", "page"}) {
            JsonNode nestedProps = nestedContainerProperties(fields, container);
            if (nestedProps == null) {
                continue;
            }
            String next = propertyByNames(nestedProps, NEXT_CURSOR_NAMES);
            if (next != null) {
                return "$." + container + "." + next;
            }
        }
        return null;
    }

    private static String inferHasMorePath(JsonNode envelope) {
        JsonNode fields = envelopeFields(envelope);
        if (fields == null) {
            return null;
        }
        String direct = propertyByNames(fields, HAS_MORE_NAMES);
        if (direct != null) {
            return "$." + direct;
        }
        for (String container : new String[]{"meta", "pagination", "page"}) {
            JsonNode nestedProps = nestedContainerProperties(fields, container);
            if (nestedProps == null) {
                continue;
            }
            String hasMore = propertyByNames(nestedProps, HAS_MORE_NAMES);
            if (hasMore != null) {
                return "$." + container + "." + hasMore;
            }
        }
        return null;
    }

    private static JsonNode envelopeFields(JsonNode envelope) {
        if (envelope == null || envelope.isMissingNode()) {
            return null;
        }
        JsonNode properties = envelope.path("properties");
        return properties.isObject() ? properties : envelope;
    }

    private static JsonNode nestedContainerProperties(JsonNode fields, String container) {
        JsonNode node = fields.path(container);
        if (!node.isObject()) {
            return null;
        }
        JsonNode properties = node.path("properties");
        return properties.isObject() ? properties : node;
    }

    private static String propertyByNames(JsonNode properties, Set<String> candidates) {
        Iterator<String> names = properties.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (candidates.contains(normalize(name))) {
                return name;
            }
        }
        return null;
    }

    private static String firstMatch(Map<String, String> params, Set<String> candidates) {
        for (String candidate : candidates) {
            String actual = params.get(candidate);
            if (actual != null) {
                return actual;
            }
        }
        return null;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static ObjectNode defaults() {
        ObjectNode pagination = JsonNodeFactory.instance.objectNode();
        pagination.put("strategy", "page_page_size");
        pagination.put("location", "query");
        pagination.put("page_param", "page");
        pagination.put("page_size_param", "page_size");
        pagination.put("page_start", 1);
        pagination.put("page_size", 100);
        pagination.put("max_pages", 1000);
        pagination.put("cursor_param", "cursor");
        pagination.put("cursor_response_path", "$.meta.nextCursor");
        pagination.put("has_more_path", "$.meta.hasMore");
        pagination.put("first_page_omit_cursor", true);
        ArrayNode stopWhen = pagination.putArray("stop_when");
        stopWhen.add("empty_cursor");
        stopWhen.add("empty_page");
        ObjectNode totalCount = pagination.putObject("total_count");
        totalCount.put("source", "none");
        totalCount.put("json_path", "");
        ObjectNode countHttp = totalCount.putObject("http");
        countHttp.put("method", "POST");
        countHttp.put("url", "");
        countHttp.put("reuse_body", true);
        return pagination;
    }
}
