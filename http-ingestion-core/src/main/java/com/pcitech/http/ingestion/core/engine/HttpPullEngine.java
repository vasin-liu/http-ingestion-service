package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.dto.TrialResponseDto;
import com.pcitech.http.ingestion.core.service.TrialRequestService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpPullEngine {

    private final TrialRequestService trialRequestService;
    private final JsonPathSupport jsonPathSupport;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public HttpPullEngine(
            TrialRequestService trialRequestService,
            JsonPathSupport jsonPathSupport,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.trialRequestService = trialRequestService;
        this.jsonPathSupport = jsonPathSupport;
        this.objectMapper = objectMapper;
    }

    public PullResult pull(
            RuntimeConnectorConfig config,
            Instant watermark,
            boolean incrementalMode,
            PullProgressListener listener
    ) {
        return pull(config, watermark, incrementalMode, null, listener);
    }

    public PullResult pull(
            RuntimeConnectorConfig config,
            Instant watermark,
            boolean incrementalMode,
            Integer maxRecords,
            PullProgressListener listener
    ) {
        RuntimeConnectorConfig.HttpSettings http = config.http();
        if (http.url() == null || http.url().isBlank()) {
            throw new IllegalArgumentException("HTTP url is required");
        }
        RuntimeConnectorConfig.PaginationSettings pagination = config.pagination() == null
                ? RuntimeConnectorConfig.PaginationSettings.defaults()
                : config.pagination();

        List<Object> allRecords = new ArrayList<>();
        Instant maxTimestamp = watermark;
        int page = pagination.pageStart();
        Integer totalPages = resolveTotalPages(config, http, pagination, watermark, incrementalMode, page);

        for (int pageIndex = 0; pageIndex < pagination.maxPages(); pageIndex++) {
            Map<String, String> query = new HashMap<>(http.query());
            String requestBody = null;
            if ("body".equalsIgnoreCase(pagination.location())) {
                requestBody = RequestBodyComposer.compose(
                        objectMapper,
                        http.bodyJson(),
                        pagination,
                        config.incremental(),
                        page,
                        watermark,
                        incrementalMode
                );
            } else {
                applyPagination(query, pagination, page);
                applyIncremental(query, config.incremental(), watermark, incrementalMode);
                if (http.bodyJson() != null && !http.bodyJson().isBlank()) {
                    requestBody = RequestBodyComposer.compose(
                            objectMapper,
                            http.bodyJson(),
                            pagination,
                            config.incremental(),
                            page,
                            watermark,
                            incrementalMode
                    );
                }
            }

            TrialResponseDto response = trialRequestService.execute(
                    HttpRequestAssembler.fromSettings(http, query, requestBody)
            );
            if (response.error() != null) {
                throw new PullException(page, "HTTP request failed: " + response.error() + " url=" + http.url());
            }
            if (response.statusCode() >= 400) {
                throw new PullException(page, "HTTP status " + response.statusCode() + " url=" + http.url());
            }

            List<Object> pageRecords = jsonPathSupport.readRecords(
                    response.body(),
                    config.transform() == null ? "$" : config.transform().inputRoot()
            );
            if (listener != null) {
                listener.onPage(page, pageRecords.size(), response.durationMs());
            }

            if (pageRecords.isEmpty()) {
                break;
            }
            for (Object record : pageRecords) {
                allRecords.add(record);
                if (maxRecords != null && allRecords.size() >= maxRecords) {
                    break;
                }
            }
            if (maxRecords != null && allRecords.size() >= maxRecords) {
                break;
            }

            if (config.incremental() != null && config.incremental().enabled()) {
                for (Object record : pageRecords) {
                    Instant ts = jsonPathSupport.readInstant(record, config.incremental().responsePath());
                    if (ts != null && (maxTimestamp == null || ts.isAfter(maxTimestamp))) {
                        maxTimestamp = ts;
                    }
                }
            }

            if (totalPages == null
                    && pagination.totalCountPath() != null
                    && !"separate_request".equalsIgnoreCase(pagination.totalCountSource())) {
                Integer total = jsonPathSupport.readInteger(response.body(), pagination.totalCountPath());
                if (total != null && pagination.pageSize() > 0) {
                    totalPages = (int) Math.ceil((double) total / pagination.pageSize());
                }
            }
            if (totalPages != null && page >= totalPages) {
                break;
            }
            page++;
        }

        if (incrementalMode && config.incremental() != null && config.incremental().enabled()
                && (maxTimestamp == null || watermark == null || !maxTimestamp.isAfter(watermark))) {
            maxTimestamp = Instant.now();
        }

        return new PullResult(allRecords, maxTimestamp);
    }

    private void applyPagination(Map<String, String> query, RuntimeConnectorConfig.PaginationSettings pagination, int page) {
        if ("offset_limit".equalsIgnoreCase(pagination.strategy())
                || "skip_limit".equalsIgnoreCase(pagination.pageValueType())) {
            int offset = (page - pagination.pageStart()) * pagination.pageSize();
            query.put(pagination.pageParam(), String.valueOf(offset));
            query.put(pagination.pageSizeParam(), String.valueOf(pagination.pageSize()));
            return;
        }
        query.put(pagination.pageParam(), String.valueOf(page));
        query.put(pagination.pageSizeParam(), String.valueOf(pagination.pageSize()));
    }

    private void applyIncremental(
            Map<String, String> query,
            RuntimeConnectorConfig.IncrementalSettings incremental,
            Instant watermark,
            boolean incrementalMode
    ) {
        if (!incrementalMode || incremental == null || !incremental.enabled() || watermark == null) {
            return;
        }
        if (!"query".equalsIgnoreCase(incremental.requestTarget())) {
            return;
        }
        Instant effective = watermark.minus(parseOverlap(incremental.overlap()));
        query.put(incremental.requestParam(), effective.toString());
    }

    private Duration parseOverlap(String overlap) {
        if (overlap == null || overlap.isBlank()) {
            return Duration.ZERO;
        }
        String value = overlap.trim().toLowerCase();
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return Duration.ofMinutes(5);
    }

    private Integer resolveTotalPages(
            RuntimeConnectorConfig config,
            RuntimeConnectorConfig.HttpSettings http,
            RuntimeConnectorConfig.PaginationSettings pagination,
            Instant watermark,
            boolean incrementalMode,
            int page
    ) {
        if (!"separate_request".equalsIgnoreCase(pagination.totalCountSource())) {
            return null;
        }
        Integer total = fetchSeparateTotal(config, http, pagination, watermark, incrementalMode, page);
        if (total == null || pagination.pageSize() <= 0) {
            return null;
        }
        return (int) Math.ceil((double) total / pagination.pageSize());
    }

    private Integer fetchSeparateTotal(
            RuntimeConnectorConfig config,
            RuntimeConnectorConfig.HttpSettings http,
            RuntimeConnectorConfig.PaginationSettings pagination,
            Instant watermark,
            boolean incrementalMode,
            int page
    ) {
        String url = pagination.totalCountUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("pagination.total_count.http.url is required for separate_request");
        }
        String method = pagination.totalCountMethod() == null || pagination.totalCountMethod().isBlank()
                ? http.method()
                : pagination.totalCountMethod();
        Map<String, String> query = new HashMap<>(http.query());
        String requestBody = null;
        if (pagination.totalCountReuseBody()) {
            if ("body".equalsIgnoreCase(pagination.location())) {
                requestBody = RequestBodyComposer.compose(
                        objectMapper,
                        http.bodyJson(),
                        pagination,
                        config.incremental(),
                        page,
                        watermark,
                        incrementalMode
                );
            } else if (http.bodyJson() != null && !http.bodyJson().isBlank()) {
                requestBody = RequestBodyComposer.compose(
                        objectMapper,
                        http.bodyJson(),
                        pagination,
                        config.incremental(),
                        page,
                        watermark,
                        incrementalMode
                );
            }
        }
        TrialResponseDto response = trialRequestService.execute(
                HttpRequestAssembler.fromSettings(http, query, requestBody)
        );
        if (response.error() != null) {
            throw new PullException(page, "Count request failed: " + response.error() + " url=" + url);
        }
        if (response.statusCode() >= 400) {
            throw new PullException(page, "Count request status " + response.statusCode() + " url=" + url);
        }
        if (pagination.totalCountPath() == null) {
            return null;
        }
        return jsonPathSupport.readInteger(response.body(), pagination.totalCountPath());
    }

    public record PullResult(List<Object> records, Instant maxTimestamp) {
    }

    public interface PullProgressListener {
        void onPage(int pageNumber, int recordCount, long durationMs);
    }

    public static class PullException extends RuntimeException {
        private final int pageNumber;

        public PullException(int pageNumber, String message) {
            super(message);
            this.pageNumber = pageNumber;
        }

        public int pageNumber() {
            return pageNumber;
        }
    }
}
