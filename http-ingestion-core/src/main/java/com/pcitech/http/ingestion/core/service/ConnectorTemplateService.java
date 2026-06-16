package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.dto.ConnectorTemplateDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConnectorTemplateService {

    private final ObjectMapper objectMapper;
    private final List<ConnectorTemplateDto> templates;

    public ConnectorTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.templates = List.of(
                restPaginationTemplate(),
                restOffsetLimitTemplate(),
                restCursorTemplate(),
                restLinkHeaderTemplate(),
                restKafkaTemplate(),
                webhookJsonArrayTemplate()
        );
    }

    public List<ConnectorTemplateDto> list() {
        return templates;
    }

    public ConnectorTemplateDto get(String id) {
        return templates.stream()
                .filter(t -> t.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + id));
    }

    private ConnectorTemplateDto restPaginationTemplate() {
        JsonNode config = objectMapper.valueToTree(java.util.Map.of(
                "http", java.util.Map.of(
                        "method", "GET",
                        "url", "https://api.example.com/items",
                        "headers", java.util.Map.of(),
                        "query", java.util.Map.of(),
                        "timeout_ms", 30000
                ),
                "pagination", java.util.Map.of(
                        "strategy", "page_page_size",
                        "page_param", "page",
                        "page_size_param", "page_size",
                        "page_start", 1,
                        "page_size", 100,
                        "max_pages", 1000,
                        "total_count", java.util.Map.of("source", "json_path", "json_path", "$.meta.total")
                ),
                "incremental", java.util.Map.of(
                        "enabled", true,
                        "timestamp", java.util.Map.of(
                                "response_path", "$.updated_at",
                                "request_param", "updated_after",
                                "overlap", "5m"
                        )
                ),
                "sync", java.util.Map.of("on_first_run", "full"),
                "transform", java.util.Map.of(
                        "input_root", "$.data",
                        "steps", List.of(java.util.Map.of(
                                "type", "map_fields",
                                "mappings", List.of(
                                        java.util.Map.of("target", "id", "source", "$.id", "type", "long"),
                                        java.util.Map.of("target", "name", "source", "$.name", "type", "string")
                                )
                        ))
                ),
                "sink", java.util.Map.of(
                        "type", "postgresql",
                        "target", java.util.Map.of("schema", "public", "table", "items"),
                        "keys", List.of("id"),
                        "write_mode", "upsert",
                        "batch_size", 500
                ),
                "schedule", java.util.Map.of(
                        "enabled", true,
                        "type", "cron",
                        "expression", "0 0/5 * * * ?"
                )
        ));
        return example(
                "rest-pagination",
                "REST 分页列表示例",
                "演示 page/page_size 分页、timestamp 增量与 PostgreSQL upsert 能力",
                "pull",
                config
        );
    }

    private ConnectorTemplateDto restOffsetLimitTemplate() {
        JsonNode config = objectMapper.valueToTree(java.util.Map.of(
                "http", java.util.Map.of(
                        "method", "GET",
                        "url", "https://api.example.com/items",
                        "headers", java.util.Map.of(),
                        "query", java.util.Map.of(),
                        "timeout_ms", 30000
                ),
                "pagination", java.util.Map.of(
                        "strategy", "offset_limit",
                        "page_param", "offset",
                        "page_size_param", "limit",
                        "page_start", 0,
                        "page_size", 100,
                        "max_pages", 1000,
                        "total_count", java.util.Map.of("source", "none")
                ),
                "incremental", java.util.Map.of("enabled", false),
                "sync", java.util.Map.of("on_first_run", "full"),
                "transform", java.util.Map.of(
                        "input_root", "$",
                        "steps", List.of(java.util.Map.of(
                                "type", "map_fields",
                                "mappings", List.of(
                                        java.util.Map.of("target", "id", "source", "$.id", "type", "long"),
                                        java.util.Map.of("target", "name", "source", "$.name", "type", "string")
                                )
                        ))
                ),
                "sink", java.util.Map.of(
                        "type", "postgresql",
                        "target", java.util.Map.of("schema", "public", "table", "items"),
                        "keys", List.of("id"),
                        "write_mode", "upsert",
                        "batch_size", 500
                ),
                "schedule", java.util.Map.of("enabled", false, "type", "cron", "expression", "0 0/5 * * * ?")
        ));
        return example(
                "rest-offset-limit",
                "REST offset/limit 示例",
                "演示 offset/limit 分页策略与 PostgreSQL upsert 能力",
                "pull",
                config
        );
    }

    private ConnectorTemplateDto restCursorTemplate() {
        JsonNode config = objectMapper.valueToTree(java.util.Map.of(
                "http", java.util.Map.of(
                        "method", "GET",
                        "url", "https://api.example.com/items",
                        "headers", java.util.Map.of(),
                        "query", java.util.Map.of(),
                        "timeout_ms", 30000
                ),
                "pagination", java.util.Map.of(
                        "strategy", "cursor",
                        "location", "query",
                        "cursor_param", "cursor",
                        "page_size_param", "limit",
                        "page_size", 100,
                        "cursor_response_path", "$.meta.nextCursor",
                        "has_more_path", "$.meta.hasMore",
                        "first_page_omit_cursor", true,
                        "stop_when", List.of("empty_cursor", "has_more_false", "empty_page"),
                        "max_pages", 1000
                ),
                "incremental", java.util.Map.of("enabled", false),
                "sync", java.util.Map.of("on_first_run", "full"),
                "transform", java.util.Map.of(
                        "input_root", "$.data",
                        "steps", List.of(java.util.Map.of(
                                "type", "map_fields",
                                "mappings", List.of(
                                        mapping("id", "$.id", "long"),
                                        mapping("name", "$.name", "string")
                                )
                        ))
                ),
                "sink", java.util.Map.of(
                        "type", "postgresql",
                        "target", java.util.Map.of("schema", "public", "table", "items"),
                        "keys", List.of("id"),
                        "write_mode", "upsert",
                        "batch_size", 500
                ),
                "schedule", java.util.Map.of(
                        "enabled", true,
                        "type", "cron",
                        "expression", "0 0/5 * * * ?"
                )
        ));
        return example(
                "rest-cursor",
                "REST Cursor 分页示例",
                "演示 cursor/token 翻页与 hasMore 终止",
                "pull",
                config
        );
    }

    private ConnectorTemplateDto restLinkHeaderTemplate() {
        JsonNode config = objectMapper.valueToTree(java.util.Map.of(
                "http", java.util.Map.of(
                        "method", "GET",
                        "url", "https://api.example.com/items",
                        "headers", java.util.Map.of(),
                        "query", java.util.Map.of(),
                        "timeout_ms", 30000
                ),
                "pagination", java.util.Map.of(
                        "strategy", "link_header",
                        "link_header_name", "Link",
                        "link_rel", "next",
                        "stop_when", List.of("no_next_link", "empty_page"),
                        "max_pages", 1000
                ),
                "incremental", java.util.Map.of("enabled", false),
                "sync", java.util.Map.of("on_first_run", "full"),
                "transform", java.util.Map.of(
                        "input_root", "$.data",
                        "steps", List.of(java.util.Map.of(
                                "type", "map_fields",
                                "mappings", List.of(
                                        mapping("id", "$.id", "long"),
                                        mapping("name", "$.name", "string")
                                )
                        ))
                ),
                "sink", java.util.Map.of(
                        "type", "postgresql",
                        "target", java.util.Map.of("schema", "public", "table", "items"),
                        "keys", List.of("id"),
                        "write_mode", "upsert",
                        "batch_size", 500
                ),
                "schedule", java.util.Map.of(
                        "enabled", true,
                        "type", "cron",
                        "expression", "0 0/5 * * * ?"
                )
        ));
        return example(
                "rest-link-header",
                "REST Link 分页示例",
                "演示 Link rel=next 响应头翻页",
                "pull",
                config
        );
    }

    private ConnectorTemplateDto restKafkaTemplate() {
        JsonNode config = objectMapper.valueToTree(java.util.Map.of(
                "http", java.util.Map.of(
                        "method", "GET",
                        "url", "https://api.example.com/items",
                        "headers", java.util.Map.of(),
                        "query", java.util.Map.of(),
                        "timeout_ms", 30000
                ),
                "pagination", java.util.Map.of(
                        "strategy", "page_page_size",
                        "page_param", "page",
                        "page_size_param", "page_size",
                        "page_start", 1,
                        "page_size", 100,
                        "max_pages", 1000,
                        "total_count", java.util.Map.of("source", "none")
                ),
                "incremental", java.util.Map.of("enabled", false),
                "sync", java.util.Map.of("on_first_run", "full"),
                "transform", java.util.Map.of(
                        "input_root", "$",
                        "steps", List.of(java.util.Map.of(
                                "type", "map_fields",
                                "mappings", List.of(
                                        java.util.Map.of("target", "id", "source", "$.id", "type", "long"),
                                        java.util.Map.of("target", "name", "source", "$.name", "type", "string")
                                )
                        ))
                ),
                "sink", java.util.Map.of(
                        "type", "kafka",
                        "target", java.util.Map.of("topic", "ingest.items"),
                        "keys", List.of("id"),
                        "serialization", "json"
                ),
                "schedule", java.util.Map.of("enabled", false, "type", "cron", "expression", "0 0/5 * * * ?")
        ));
        return example(
                "rest-kafka",
                "REST → Kafka 示例",
                "演示 Pull 分页并将 JSON 记录写入 Kafka topic",
                "pull",
                config,
                kafkaUserRecordSchema(),
                kafkaUserSampleResponse()
        );
    }

    private ConnectorTemplateDto webhookJsonArrayTemplate() {
        JsonNode config = objectMapper.valueToTree(java.util.Map.of(
                "http", java.util.Map.of(
                        "method", "POST",
                        "url", "",
                        "headers", java.util.Map.of("Content-Type", "application/json"),
                        "query", java.util.Map.of(),
                        "timeout_ms", 30000
                ),
                "transform", java.util.Map.of(
                        "input_root", "$",
                        "steps", List.of(java.util.Map.of(
                                "type", "map_fields",
                                "mappings", List.of(
                                        java.util.Map.of("target", "id", "source", "$.id", "type", "long")
                                )
                        ))
                ),
                "sink", java.util.Map.of(
                        "type", "postgresql",
                        "target", java.util.Map.of("schema", "public", "table", "webhook_events"),
                        "keys", List.of("id"),
                        "write_mode", "upsert",
                        "batch_size", 500
                ),
                "schedule", java.util.Map.of("enabled", false, "type", "cron", "expression", "0 0/5 * * * ?")
        ));
        return example(
                "webhook-json-array",
                "Webhook JSON 数组示例",
                "演示 Push 模式与 JSON 根路径映射能力",
                "push",
                config
        );
    }


    private ConnectorTemplateDto example(String id, String name, String description, String mode, JsonNode config) {
        return example(id, name, description, mode, config, null, null);
    }

    private ConnectorTemplateDto example(
            String id,
            String name,
            String description,
            String mode,
            JsonNode config,
            JsonNode responseSchema,
            JsonNode sampleResponse
    ) {
        return new ConnectorTemplateDto(id, name, description, mode, "example", responseSchema, sampleResponse, null, config);
    }

    private JsonNode recordSchema(String inputRoot, java.util.Map<String, Object> properties, java.util.Map<String, Object> envelope) {
        return objectMapper.valueToTree(java.util.Map.of(
                "input_root", inputRoot,
                "record", java.util.Map.of("type", "object", "properties", properties),
                "envelope", envelope
        ));
    }

    private JsonNode kafkaUserRecordSchema() {
        return recordSchema("$", java.util.Map.of(
                "id", prop("integer", 1),
                "name", prop("string", "Alice")
        ), java.util.Map.of());
    }

    private JsonNode kafkaUserSampleResponse() {
        return parseJson("""
                [{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]
                """);
    }

    private java.util.Map<String, Object> prop(String type, Object example) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("type", type);
        map.put("example", example);
        return map;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid template JSON", ex);
        }
    }

    private static java.util.Map<String, String> mapping(String target, String source, String type) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("target", target);
        map.put("source", source);
        map.put("type", type);
        return map;
    }
}
