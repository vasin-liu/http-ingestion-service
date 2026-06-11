package com.pcitech.http.ingestion.core.config.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RuntimeConfigParser {

    private RuntimeConfigParser() {
    }

    public static RuntimeConnectorConfig parse(JsonNode root, ObjectMapper mapper) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Connector config must be a JSON object");
        }
        return new RuntimeConnectorConfig(
                text(root, "mode", "pull"),
                parseHttp(root.path("http")),
                parsePagination(root.path("pagination")),
                parseIncremental(root.path("incremental")),
                parseSync(root.path("sync")),
                parseTransform(root.path("transform")),
                parseSink(root.path("sink")),
                parseSchedule(root.path("schedule")),
                parseWebhook(root.path("webhook"))
        );
    }

    private static RuntimeConnectorConfig.HttpSettings parseHttp(JsonNode node) {
        Map<String, String> headers = readStringMap(node.path("headers"));
        Map<String, String> query = readStringMap(node.path("query"));
        Map<String, String> form = readStringMap(node.path("form"));
        JsonNode body = node.path("body");
        String bodyJson = body.isMissingNode() || body.isNull() ? null : bodyToJson(body);
        String bodyType = text(node, "body_type", null);
        if (bodyType == null || bodyType.isBlank()) {
            if (bodyJson != null && !bodyJson.isBlank()) {
                bodyType = "json";
            } else if (!form.isEmpty()) {
                bodyType = "form-urlencoded";
            } else {
                bodyType = "none";
            }
        }
        int timeoutMs = node.path("timeout_ms").asInt(-1);
        if (timeoutMs < 0) {
            timeoutMs = node.path("timeoutMs").asInt(30000);
        }
        return new RuntimeConnectorConfig.HttpSettings(
                text(node, "method", "GET"),
                text(node, "url", null),
                headers,
                query,
                bodyType,
                bodyJson,
                form,
                timeoutMs
        );
    }

    private static String bodyToJson(JsonNode body) {
        if (body.isTextual()) {
            return body.asText();
        }
        return body.toString();
    }

    private static RuntimeConnectorConfig.PaginationSettings parsePagination(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return RuntimeConnectorConfig.PaginationSettings.defaults();
        }
        JsonNode total = node.path("total_count");
        String totalPath = total.path("json_path").asText(null);
        if (totalPath != null && totalPath.isBlank()) {
            totalPath = null;
        }
        String totalSource = text(total, "source", null);
        if (totalSource == null || totalSource.isBlank()) {
            totalSource = totalPath != null ? "json_path" : "none";
        }
        JsonNode countHttp = total.path("http");
        String countUrl = countHttp.path("url").asText(null);
        if (countUrl != null && countUrl.isBlank()) {
            countUrl = null;
        }
        String countMethod = text(countHttp, "method", "POST");
        boolean reuseBody = !countHttp.has("reuse_body") || countHttp.path("reuse_body").asBoolean(true);
        List<String> stopWhen = new ArrayList<>();
        JsonNode stopNode = node.path("stop_when");
        if (stopNode.isArray()) {
            for (JsonNode item : stopNode) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    stopWhen.add(item.asText().trim());
                }
            }
        }
        if (stopWhen.isEmpty()) {
            stopWhen = new ArrayList<>(RuntimeConnectorConfig.PaginationSettings.defaultStopWhen());
        }
        return new RuntimeConnectorConfig.PaginationSettings(
                text(node, "strategy", "page_page_size"),
                text(node, "location", "query"),
                text(node, "page_value_type", "page_number"),
                text(node, "page_param", "page"),
                text(node, "page_size_param", "page_size"),
                node.path("page_start").asInt(1),
                node.path("page_size").asInt(100),
                totalPath,
                node.path("max_pages").asInt(1000),
                totalSource,
                countUrl,
                countMethod,
                reuseBody,
                text(node, "cursor_param", null),
                text(node, "cursor_response_path", null),
                text(node, "has_more_path", null),
                !node.has("first_page_omit_cursor") || node.path("first_page_omit_cursor").asBoolean(true),
                stopWhen
        );
    }

    private static RuntimeConnectorConfig.IncrementalSettings parseIncremental(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.path("enabled").asBoolean(false)) {
            return RuntimeConnectorConfig.IncrementalSettings.disabled();
        }
        JsonNode ts = node.path("timestamp");
        return new RuntimeConnectorConfig.IncrementalSettings(
                true,
                ts.path("response_path").asText("$.updated_at"),
                ts.path("request_param").asText("updated_after"),
                text(ts, "request_target", text(node, "request_target", "query")),
                ts.path("request_body_path").asText(null),
                ts.path("request_body_end_path").asText(null),
                text(ts, "format", "iso_instant"),
                ts.path("overlap").asText("5m")
        );
    }

    private static RuntimeConnectorConfig.SyncSettings parseSync(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return RuntimeConnectorConfig.SyncSettings.defaults();
        }
        return new RuntimeConnectorConfig.SyncSettings(text(node, "on_first_run", "full"));
    }

    public static RuntimeConnectorConfig.TransformSettings parseTransformOnly(JsonNode transformNode) {
        return parseTransform(transformNode == null ? null : transformNode);
    }

    private static RuntimeConnectorConfig.TransformSettings parseTransform(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new RuntimeConnectorConfig.TransformSettings("$.data", List.of());
        }
        List<RuntimeConnectorConfig.TransformStep> steps = new ArrayList<>();
        for (JsonNode step : node.path("steps")) {
            steps.add(parseStep(step));
        }
        return new RuntimeConnectorConfig.TransformSettings(
                text(node, "input_root", "$.data"),
                steps
        );
    }

    private static RuntimeConnectorConfig.TransformStep parseStep(JsonNode step) {
        String type = text(step, "type", "");
        List<RuntimeConnectorConfig.FieldMapping> mappings = new ArrayList<>();
        for (JsonNode m : step.path("mappings")) {
            mappings.add(new RuntimeConnectorConfig.FieldMapping(
                    text(m, "target", null),
                    text(m, "source", null),
                    text(m, "type", "string"),
                    text(m, "source_format", null),
                    text(m, "target_format", null)
            ));
        }
        Map<String, String> expressions = new HashMap<>();
        JsonNode setNode = step.path("set");
        if (setNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = setNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                expressions.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return new RuntimeConnectorConfig.TransformStep(
                type,
                text(step, "condition", null),
                mappings,
                expressions
        );
    }

    private static RuntimeConnectorConfig.SinkSettings parseSink(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode target = node.path("target");
        List<String> keys = new ArrayList<>();
        for (JsonNode key : node.path("keys")) {
            keys.add(key.asText());
        }
        return new RuntimeConnectorConfig.SinkSettings(
                text(node, "type", "postgresql"),
                text(target, "schema", "public"),
                text(target, "table", null),
                keys,
                text(node, "write_mode", "upsert"),
                node.path("batch_size").asInt(500),
                text(target, "topic", null),
                text(node, "serialization", "json")
        );
    }

    private static RuntimeConnectorConfig.ScheduleSettings parseSchedule(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return RuntimeConnectorConfig.ScheduleSettings.disabled();
        }
        return new RuntimeConnectorConfig.ScheduleSettings(
                node.path("enabled").asBoolean(true),
                text(node, "type", "cron"),
                text(node, "expression", "0 0/5 * * * ?"),
                node.hasNonNull("interval_seconds") ? node.path("interval_seconds").asInt() : null
        );
    }

    private static RuntimeConnectorConfig.WebhookSettings parseWebhook(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return RuntimeConnectorConfig.WebhookSettings.defaults();
        }
        return new RuntimeConnectorConfig.WebhookSettings(
                node.path("enabled").asBoolean(true),
                text(node, "path_suffix", ""),
                node.path("verify_sign").asBoolean(false),
                text(node, "plat_flag", "ivsp")
        );
    }

    private static Map<String, String> readStringMap(JsonNode node) {
        Map<String, String> map = new HashMap<>();
        if (node != null && node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                map.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return map;
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText();
        return text.isBlank() ? defaultValue : text;
    }
}
