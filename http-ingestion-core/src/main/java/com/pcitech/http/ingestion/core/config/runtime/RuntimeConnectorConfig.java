package com.pcitech.http.ingestion.core.config.runtime;

import java.util.List;
import java.util.Map;

public record RuntimeConnectorConfig(
        String mode,
        HttpSettings http,
        PaginationSettings pagination,
        IncrementalSettings incremental,
        SyncSettings sync,
        TransformSettings transform,
        SinkSettings sink,
        ScheduleSettings schedule,
        WebhookSettings webhook
) {
    public record HttpSettings(
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> query,
            String bodyType,
            String bodyJson,
            Map<String, String> form,
            int timeoutMs
    ) {
    }

    public record PaginationSettings(
            String strategy,
            String location,
            String pageValueType,
            String pageParam,
            String pageSizeParam,
            int pageStart,
            int pageSize,
            String totalCountPath,
            int maxPages,
            String totalCountSource,
            String totalCountUrl,
            String totalCountMethod,
            boolean totalCountReuseBody,
            String cursorParam,
            String cursorResponsePath,
            String hasMorePath,
            boolean firstPageOmitCursor,
            List<String> stopWhen,
            String linkHeaderName,
            String linkRel
    ) {
        public static PaginationSettings defaults() {
            return new PaginationSettings(
                    "page_page_size", "query", "page_number", "page", "page_size", 1, 100, null, 1000,
                    "none", null, null, true,
                    null, null, null, true, defaultStopWhen(),
                    "Link", "next"
            );
        }

        public static List<String> defaultStopWhen() {
            return List.of("empty_cursor", "empty_page");
        }

        public static List<String> defaultLinkHeaderStopWhen() {
            return List.of("no_next_link", "empty_page");
        }
    }

    public record IncrementalSettings(
            boolean enabled,
            String mode,
            String responsePath,
            String requestParam,
            String requestTarget,
            String requestBodyPath,
            String requestBodyEndPath,
            String timeFormat,
            String overlap
    ) {
        public static IncrementalSettings disabled() {
            return new IncrementalSettings(false, "timestamp", null, null, "query", null, null, "iso_instant", "5m");
        }

        public boolean isMonotonicId() {
            return "monotonic_id".equalsIgnoreCase(mode);
        }
    }

    public record SyncSettings(String onFirstRun) {
        public static SyncSettings defaults() {
            return new SyncSettings("full");
        }
    }

    public record TransformSettings(
            String inputRoot,
            List<TransformStep> steps
    ) {
    }

    public record TransformStep(String type, String condition, List<FieldMapping> mappings, Map<String, String> expressions) {
    }

    public record FieldMapping(String target, String source, String type, String sourceFormat, String targetFormat) {
        public FieldMapping(String target, String source, String type) {
            this(target, source, type, null, null);
        }
    }

    public record SinkSettings(
            String type,
            String schema,
            String table,
            List<String> keys,
            String writeMode,
            int batchSize,
            String topic,
            String serialization
    ) {
    }

    public record ScheduleSettings(
            boolean enabled,
            String type,
            String expression,
            Integer intervalSeconds
    ) {
        public static ScheduleSettings disabled() {
            return new ScheduleSettings(false, "cron", "0 0/5 * * * ?", null);
        }
    }

    public record WebhookSettings(
            boolean enabled,
            String pathSuffix,
            boolean verifySign,
            String platFlag
    ) {
        public static WebhookSettings defaults() {
            return new WebhookSettings(true, "", false, "ivsp");
        }
    }
}
