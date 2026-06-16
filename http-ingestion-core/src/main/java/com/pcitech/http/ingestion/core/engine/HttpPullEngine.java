package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.domain.WatermarkState;
import com.pcitech.http.ingestion.core.dto.TrialResponseDto;
import com.pcitech.http.ingestion.core.service.TrialRequestService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            WatermarkState watermark,
            boolean incrementalMode,
            PullProgressListener listener
    ) {
        return pull(config, watermark, incrementalMode, null, listener);
    }

    public PullResult pull(
            RuntimeConnectorConfig config,
            WatermarkState watermark,
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

        if ("cursor".equalsIgnoreCase(pagination.strategy())) {
            return pullWithCursor(config, watermark, incrementalMode, maxRecords, listener, pagination, http);
        }
        if ("link_header".equalsIgnoreCase(pagination.strategy())) {
            return pullWithLinkHeader(config, watermark, incrementalMode, maxRecords, listener, pagination, http);
        }

        WatermarkState wmState = watermark == null ? WatermarkState.empty() : watermark;
        List<Object> allRecords = new ArrayList<>();
        int page = pagination.pageStart();
        Integer totalPages = resolveTotalPages(config, http, pagination, wmState, incrementalMode, page);

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
                        wmState,
                        incrementalMode
                );
            } else {
                applyPagination(query, pagination, page);
                IncrementalSupport.applyQuery(query, config.incremental(), wmState, incrementalMode);
                if (http.bodyJson() != null && !http.bodyJson().isBlank()) {
                    requestBody = RequestBodyComposer.compose(
                            objectMapper,
                            http.bodyJson(),
                            pagination,
                            config.incremental(),
                            page,
                            wmState,
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

            wmState = IncrementalSupport.advance(wmState, pageRecords, config.incremental(), jsonPathSupport);

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

        wmState = IncrementalSupport.bumpTimestampIfUnchanged(
                wmState, watermark, config.incremental(), incrementalMode);

        return new PullResult(allRecords, wmState);
    }

    private PullResult pullWithCursor(
            RuntimeConnectorConfig config,
            WatermarkState watermark,
            boolean incrementalMode,
            Integer maxRecords,
            PullProgressListener listener,
            RuntimeConnectorConfig.PaginationSettings pagination,
            RuntimeConnectorConfig.HttpSettings http
    ) {
        if (pagination.cursorResponsePath() == null || pagination.cursorResponsePath().isBlank()) {
            throw new IllegalArgumentException("pagination.cursor_response_path is required for cursor strategy");
        }

        WatermarkState wmState = watermark == null ? WatermarkState.empty() : watermark;
        List<Object> allRecords = new ArrayList<>();
        String cursor = null;
        boolean firstPage = true;

        for (int pageIndex = 0; pageIndex < pagination.maxPages(); pageIndex++) {
            Map<String, String> query = new HashMap<>(http.query());
            String requestBody = null;

            if ("body".equalsIgnoreCase(pagination.location())) {
                requestBody = RequestBodyComposer.composeWithCursor(
                        objectMapper,
                        http.bodyJson(),
                        pagination,
                        config.incremental(),
                        cursor,
                        firstPage,
                        wmState,
                        incrementalMode
                );
            } else {
                CursorPaginationSupport.applyCursorQuery(query, pagination, cursor, firstPage);
                IncrementalSupport.applyQuery(query, config.incremental(), wmState, incrementalMode);
                if (http.bodyJson() != null && !http.bodyJson().isBlank()) {
                    requestBody = RequestBodyComposer.composeWithCursor(
                            objectMapper,
                            http.bodyJson(),
                            pagination,
                            config.incremental(),
                            cursor,
                            firstPage,
                            wmState,
                            incrementalMode
                    );
                }
            }

            TrialResponseDto response = trialRequestService.execute(
                    HttpRequestAssembler.fromSettings(http, query, requestBody)
            );
            if (response.error() != null) {
                throw new PullException(pageIndex, "HTTP request failed: " + response.error() + " url=" + http.url());
            }
            if (response.statusCode() >= 400) {
                throw new PullException(pageIndex, "HTTP status " + response.statusCode() + " url=" + http.url());
            }

            List<Object> pageRecords = jsonPathSupport.readRecords(
                    response.body(),
                    config.transform() == null ? "$" : config.transform().inputRoot()
            );
            if (listener != null) {
                listener.onPage(pageIndex, pageRecords.size(), response.durationMs());
            }

            for (Object record : pageRecords) {
                allRecords.add(record);
                if (maxRecords != null && allRecords.size() >= maxRecords) {
                    break;
                }
            }

            wmState = IncrementalSupport.advance(wmState, pageRecords, config.incremental(), jsonPathSupport);

            if (maxRecords != null && allRecords.size() >= maxRecords) {
                break;
            }

            String nextCursor = jsonPathSupport.readString(response.body(), pagination.cursorResponsePath());
            Boolean hasMore = jsonPathSupport.readBoolean(response.body(), pagination.hasMorePath());
            if (CursorPaginationSupport.shouldStop(pagination, pageRecords, nextCursor, hasMore)) {
                break;
            }

            cursor = nextCursor;
            firstPage = false;
        }

        wmState = IncrementalSupport.bumpTimestampIfUnchanged(
                wmState, watermark, config.incremental(), incrementalMode);

        return new PullResult(allRecords, wmState);
    }

    private PullResult pullWithLinkHeader(
            RuntimeConnectorConfig config,
            WatermarkState watermark,
            boolean incrementalMode,
            Integer maxRecords,
            PullProgressListener listener,
            RuntimeConnectorConfig.PaginationSettings pagination,
            RuntimeConnectorConfig.HttpSettings http
    ) {
        List<Object> allRecords = new ArrayList<>();
        WatermarkState wmState = watermark == null ? WatermarkState.empty() : watermark;
        String currentUrl = http.url();
        boolean firstPage = true;

        for (int pageIndex = 0; pageIndex < pagination.maxPages(); pageIndex++) {
            Map<String, String> query = firstPage ? new HashMap<>(http.query()) : Map.of();
            if (firstPage) {
                IncrementalSupport.applyQuery(query, config.incremental(), wmState, incrementalMode);
            }
            String requestBody = null;
            if (firstPage && http.bodyJson() != null && !http.bodyJson().isBlank()) {
                requestBody = RequestBodyComposer.compose(
                        objectMapper,
                        http.bodyJson(),
                        pagination,
                        config.incremental(),
                        pagination.pageStart(),
                        wmState,
                        incrementalMode
                );
            }

            TrialResponseDto response = trialRequestService.execute(
                    HttpRequestAssembler.fromSettings(http, currentUrl, query, requestBody)
            );
            if (response.error() != null) {
                throw new PullException(pageIndex, "HTTP request failed: " + response.error() + " url=" + currentUrl);
            }
            if (response.statusCode() >= 400) {
                throw new PullException(pageIndex, "HTTP status " + response.statusCode() + " url=" + currentUrl);
            }

            List<Object> pageRecords = jsonPathSupport.readRecords(
                    response.body(),
                    config.transform() == null ? "$" : config.transform().inputRoot()
            );
            if (listener != null) {
                listener.onPage(pageIndex, pageRecords.size(), response.durationMs());
            }

            for (Object record : pageRecords) {
                allRecords.add(record);
                if (maxRecords != null && allRecords.size() >= maxRecords) {
                    break;
                }
            }

            wmState = IncrementalSupport.advance(wmState, pageRecords, config.incremental(), jsonPathSupport);

            if (maxRecords != null && allRecords.size() >= maxRecords) {
                break;
            }

            String linkHeader = LinkHeaderSupport.readHeader(
                    response.responseHeaders(),
                    pagination.linkHeaderName() == null ? "Link" : pagination.linkHeaderName()
            );
            String rel = pagination.linkRel() == null || pagination.linkRel().isBlank() ? "next" : pagination.linkRel();
            Optional<String> nextLink = LinkHeaderSupport.parseRelLink(linkHeader, rel);
            if (LinkHeaderSupport.shouldStop(pagination, pageRecords, nextLink)) {
                break;
            }

            currentUrl = nextLink.orElseThrow();
            firstPage = false;
        }

        wmState = IncrementalSupport.bumpTimestampIfUnchanged(
                wmState, watermark, config.incremental(), incrementalMode);

        return new PullResult(allRecords, wmState);
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

    private Integer resolveTotalPages(
            RuntimeConnectorConfig config,
            RuntimeConnectorConfig.HttpSettings http,
            RuntimeConnectorConfig.PaginationSettings pagination,
            WatermarkState watermark,
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
            WatermarkState watermark,
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

    public record PullResult(List<Object> records, WatermarkState watermarkState) {
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
