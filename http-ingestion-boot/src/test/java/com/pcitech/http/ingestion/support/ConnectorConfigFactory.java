package com.pcitech.http.ingestion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ConnectorConfigFactory {

    private ConnectorConfigFactory() {
    }

    public static JsonNode genericWireMockUsersConfig(ObjectMapper objectMapper, String wireMockBaseUrl) {
        ObjectNode config = objectMapper.createObjectNode();

        ObjectNode http = config.putObject("http");
        http.put("method", "GET");
        http.put("url", wireMockBaseUrl + "/users");
        http.put("timeout_ms", 30000);
        http.putObject("headers");
        http.putObject("query");

        ObjectNode pagination = config.putObject("pagination");
        pagination.put("strategy", "page_page_size");
        pagination.put("page_param", "page");
        pagination.put("page_size_param", "page_size");
        pagination.put("page_start", 1);
        pagination.put("page_size", 100);
        pagination.put("max_pages", 1);
        pagination.putObject("total_count").put("source", "none");

        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "id").put("source", "$.id").put("type", "long");
        mappings.addObject().put("target", "name").put("source", "$.name").put("type", "string");

        appendSink(config, "users", "id");
        config.putObject("incremental").put("enabled", false);
        return config;
    }

    public static JsonNode dahuaVehicleQueryConfig(ObjectMapper objectMapper, String baseUrl) {
        ObjectNode config = objectMapper.createObjectNode();
        ObjectNode http = config.putObject("http");
        http.put("method", "POST");
        http.put("url", baseUrl + "/mock/dahua/gretrieval/vehicle/query");
        http.put("timeout_ms", 30000);
        http.putObject("headers").put("Content-Type", "application/json");
        http.putObject("query");
        ObjectNode body = http.putObject("body");
        body.put("page", 1);
        body.put("pageSize", 100);
        body.putObject("condition");

        ObjectNode pagination = config.putObject("pagination");
        pagination.put("strategy", "page_page_size");
        pagination.put("location", "body");
        pagination.put("page_value_type", "page_number");
        pagination.put("page_param", "page");
        pagination.put("page_size_param", "pageSize");
        pagination.put("page_start", 1);
        pagination.put("page_size", 100);
        pagination.put("max_pages", 20);
        ObjectNode totalCount = pagination.putObject("total_count");
        totalCount.put("source", "separate_request");
        totalCount.put("json_path", "$.totalCount");
        ObjectNode countHttp = totalCount.putObject("http");
        countHttp.put("method", "POST");
        countHttp.put("url", baseUrl + "/mock/dahua/gretrieval/vehicle/count");
        countHttp.put("reuse_body", true);

        ObjectNode incremental = config.putObject("incremental");
        incremental.put("enabled", true);
        ObjectNode timestamp = incremental.putObject("timestamp");
        timestamp.put("response_path", "$.capTime");
        timestamp.put("request_target", "body");
        timestamp.put("request_body_path", "condition.startTime");
        timestamp.put("request_body_end_path", "condition.endTime");
        timestamp.put("format", "dahua_utc");
        timestamp.put("overlap", "5m");

        appendDahuaVehicleMappings(config);
        appendSink(config, "dahua_vehicle_pass", "record_id");
        return config;
    }

    public static JsonNode dahuaMotorIllegalQueryConfig(ObjectMapper objectMapper, String baseUrl) {
        ObjectNode config = objectMapper.createObjectNode();
        ObjectNode http = config.putObject("http");
        http.put("method", "POST");
        http.put("url", baseUrl + "/mock/dahua/jg/trafficVehicle/illegal/queryList");
        http.put("timeout_ms", 30000);
        http.putObject("headers").put("Content-Type", "application/json");
        http.putObject("query");
        http.putObject("body");

        ObjectNode pagination = config.putObject("pagination");
        pagination.put("strategy", "page_page_size");
        pagination.put("location", "query");
        pagination.put("page_value_type", "page_number");
        pagination.put("page_param", "page");
        pagination.put("page_size_param", "pageSize");
        pagination.put("page_start", 1);
        pagination.put("page_size", 100);
        pagination.put("max_pages", 20);
        ObjectNode totalCount = pagination.putObject("total_count");
        totalCount.put("source", "separate_request");
        totalCount.put("json_path", "$.totalCount");
        ObjectNode countHttp = totalCount.putObject("http");
        countHttp.put("method", "POST");
        countHttp.put("url", baseUrl + "/mock/dahua/jg/trafficVehicle/illegal/count");
        countHttp.put("reuse_body", true);

        ObjectNode incremental = config.putObject("incremental");
        incremental.put("enabled", true);
        ObjectNode timestamp = incremental.putObject("timestamp");
        timestamp.put("response_path", "$.capTime");
        timestamp.put("request_target", "body");
        timestamp.put("request_body_path", "startTimeStrUtc");
        timestamp.put("request_body_end_path", "endTimeStrUtc");
        timestamp.put("format", "dahua_utc");
        timestamp.put("overlap", "5m");

        appendDahuaMotorIllegalMappings(config);
        appendSink(config, "dahua_motor_illegal", "record_id");
        return config;
    }

    public static JsonNode meiyaTrafficPoliceConfig(ObjectMapper objectMapper, String baseUrl) {
        ObjectNode config = meiyaBaseConfig(objectMapper, baseUrl, "/mock/meiya/api/res/trafficPoliceAlert");
        ObjectNode incremental = config.putObject("incremental");
        incremental.put("enabled", true);
        ObjectNode timestamp = incremental.putObject("timestamp");
        timestamp.put("response_path", "$.evcc");
        timestamp.put("request_target", "body");
        timestamp.put("request_body_path", "params.evcc");
        timestamp.put("format", "meiya_datetime");
        timestamp.put("overlap", "5m");

        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$.data");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "jqbh").put("source", "$.jqbh").put("type", "string");
        mappings.addObject().put("target", "jqfssj").put("source", "$.jqfssj").put("type", "string");
        mappings.addObject().put("target", "evcc").put("source", "$.evcc").put("type", "string");
        mappings.addObject().put("target", "desct").put("source", "$.desct").put("type", "string");

        appendSink(config, "meiya_traffic_police_alert", "jqbh");
        return config;
    }

    public static JsonNode meiyaDispatch110Config(ObjectMapper objectMapper, String baseUrl) {
        ObjectNode config = meiyaBaseConfig(objectMapper, baseUrl, "/mock/meiya/api/res/dispatch110Flow");
        ObjectNode incremental = config.putObject("incremental");
        incremental.put("enabled", true);
        ObjectNode timestamp = incremental.putObject("timestamp");
        timestamp.put("response_path", "$.gxsj");
        timestamp.put("request_target", "body");
        timestamp.put("request_body_path", "params.gxsj");
        timestamp.put("format", "meiya_datetime");
        timestamp.put("overlap", "5m");

        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$.data");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "bh").put("source", "$.bh").put("type", "string");
        mappings.addObject().put("target", "jjdbh").put("source", "$.jjdbh").put("type", "string");
        mappings.addObject().put("target", "gxsj").put("source", "$.gxsj").put("type", "string");
        mappings.addObject().put("target", "xxlxms").put("source", "$.xxlxms").put("type", "string");

        appendSink(config, "meiya_dispatch110_flow", "bh");
        return config;
    }

    private static ObjectNode meiyaBaseConfig(ObjectMapper objectMapper, String baseUrl, String path) {
        ObjectNode config = objectMapper.createObjectNode();
        ObjectNode http = config.putObject("http");
        http.put("method", "POST");
        http.put("url", baseUrl + path);
        http.put("timeout_ms", 30000);
        http.putObject("headers").put("Content-Type", "application/json");
        http.putObject("query");
        ObjectNode body = http.putObject("body");
        body.putObject("params");
        body.putObject("page").put("skip", 0).put("limit", 100);

        ObjectNode pagination = config.putObject("pagination");
        pagination.put("strategy", "page_page_size");
        pagination.put("location", "body");
        pagination.put("page_value_type", "skip_limit");
        pagination.put("page_param", "page.skip");
        pagination.put("page_size_param", "page.limit");
        pagination.put("page_start", 1);
        pagination.put("page_size", 100);
        pagination.put("max_pages", 20);
        pagination.putObject("total_count").put("json_path", "$.states.total");
        return config;
    }

    private static void appendDahuaVehicleMappings(ObjectNode config) {
        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$.results");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "record_id").put("source", "$.recordId").put("type", "string");
        mappings.addObject().put("target", "plate_num").put("source", "$.plateNum").put("type", "string");
        mappings.addObject().put("target", "cap_time").put("source", "$.capTime").put("type", "string");
        mappings.addObject().put("target", "channel_name").put("source", "$.channelName").put("type", "string");
        mappings.addObject().put("target", "plate_type").put("source", "$.plateType").put("type", "string");
    }

    private static void appendDahuaMotorIllegalMappings(ObjectNode config) {
        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$.results");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "record_id").put("source", "$.recordId").put("type", "string");
        mappings.addObject().put("target", "plate_num").put("source", "$.plateNum").put("type", "string");
        mappings.addObject().put("target", "cap_time").put("source", "$.capTime").put("type", "long");
        mappings.addObject().put("target", "rec_type").put("source", "$.recType").put("type", "long");
        mappings.addObject().put("target", "channel_name").put("source", "$.channelName").put("type", "string");
        mappings.addObject().put("target", "plate_type").put("source", "$.plateType").put("type", "string");
    }

    private static void appendSink(ObjectNode config, String table, String key) {
        ObjectNode sink = config.putObject("sink");
        sink.put("type", "postgresql");
        sink.putObject("target").put("schema", "public").put("table", table);
        sink.putArray("keys").add(key);
        sink.put("write_mode", "upsert");
        sink.put("batch_size", 500);
    }

    public static void withSchedule(ObjectNode config, boolean enabled, String cronExpression) {
        ObjectNode schedule = config.putObject("schedule");
        schedule.put("enabled", enabled);
        schedule.put("type", "cron");
        schedule.put("expression", cronExpression);
    }

    public static void withFixedRateSchedule(ObjectNode config, boolean enabled, int intervalSeconds) {
        ObjectNode schedule = config.putObject("schedule");
        schedule.put("enabled", enabled);
        schedule.put("type", "fixed_rate");
        schedule.put("interval_seconds", intervalSeconds);
    }

    public static JsonNode jiaduEventPushConfig(ObjectMapper objectMapper, boolean verifySign, String platFlag) {
        ObjectNode config = objectMapper.createObjectNode();
        ObjectNode webhook = config.putObject("webhook");
        webhook.put("enabled", true);
        webhook.put("path_suffix", "");
        webhook.put("verify_sign", verifySign);
        webhook.put("plat_flag", platFlag);

        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "event_id").put("source", "$.EventID").put("type", "string");
        mappings.addObject().put("target", "event_type").put("source", "$.EventType").put("type", "long");
        mappings.addObject().put("target", "event_name").put("source", "$.EventName").put("type", "string");
        mappings.addObject().put("target", "send_time").put("source", "$.SendTime").put("type", "string");
        mappings.addObject().put("target", "camera_id").put("source", "$.CameraID").put("type", "string");
        mappings.addObject().put("target", "img_url").put("source", "$.ImgUrl").put("type", "string");
        mappings.addObject().put("target", "video_url").put("source", "$.VideoUrl").put("type", "string");
        mappings.addObject().put("target", "event_time").put("source", "$.EventTime").put("type", "string");
        mappings.addObject().put("target", "confidence").put("source", "$.Confidence").put("type", "double");
        mappings.addObject().put("target", "task_id").put("source", "$.TaskID").put("type", "string");
        mappings.addObject().put("target", "event_group").put("source", "$.EventGroup").put("type", "long");
        mappings.addObject().put("target", "census").put("source", "$.Census").put("type", "long");
        mappings.addObject().put("target", "inter_day").put("source", "$.InterDay").put("type", "long");
        mappings.addObject().put("target", "enter_number").put("source", "$.EnterNumber").put("type", "long");
        mappings.addObject().put("target", "out_number").put("source", "$.OutNumber").put("type", "long");

        appendSink(config, "jiadu_event_info", "event_id");
        config.putObject("schedule").put("enabled", false);
        return config;
    }

    public static JsonNode offsetLimitItemsConfig(ObjectMapper objectMapper, String baseUrl) {
        ObjectNode config = objectMapper.createObjectNode();
        ObjectNode http = config.putObject("http");
        http.put("method", "GET");
        http.put("url", baseUrl + "/items");
        http.put("timeout_ms", 30000);
        http.putObject("headers");
        http.putObject("query");

        ObjectNode pagination = config.putObject("pagination");
        pagination.put("strategy", "offset_limit");
        pagination.put("page_param", "offset");
        pagination.put("page_size_param", "limit");
        pagination.put("page_start", 0);
        pagination.put("page_size", 2);
        pagination.put("max_pages", 10);
        pagination.putObject("total_count").put("source", "none");

        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "id").put("source", "$.id").put("type", "long");
        mappings.addObject().put("target", "name").put("source", "$.name").put("type", "string");

        appendSink(config, "users", "id");
        config.putObject("incremental").put("enabled", false);
        return config;
    }

    public static JsonNode kafkaWireMockUsersConfig(ObjectMapper objectMapper, String wireMockBaseUrl, String topic) {
        ObjectNode config = objectMapper.createObjectNode();

        ObjectNode http = config.putObject("http");
        http.put("method", "GET");
        http.put("url", wireMockBaseUrl + "/users");
        http.put("timeout_ms", 30000);
        http.putObject("headers");
        http.putObject("query");

        ObjectNode pagination = config.putObject("pagination");
        pagination.put("strategy", "page_page_size");
        pagination.put("page_param", "page");
        pagination.put("page_size_param", "page_size");
        pagination.put("page_start", 1);
        pagination.put("page_size", 100);
        pagination.put("max_pages", 1);
        pagination.putObject("total_count").put("source", "none");

        ObjectNode transform = config.putObject("transform");
        transform.put("input_root", "$");
        ArrayNode steps = transform.putArray("steps");
        ObjectNode step = steps.addObject();
        step.put("type", "map_fields");
        ArrayNode mappings = step.putArray("mappings");
        mappings.addObject().put("target", "id").put("source", "$.id").put("type", "long");
        mappings.addObject().put("target", "name").put("source", "$.name").put("type", "string");

        ObjectNode sink = config.putObject("sink");
        sink.put("type", "kafka");
        sink.putObject("target").put("topic", topic);
        sink.putArray("keys").add("id");
        sink.put("serialization", "json");

        config.putObject("incremental").put("enabled", false);
        return config;
    }
}
