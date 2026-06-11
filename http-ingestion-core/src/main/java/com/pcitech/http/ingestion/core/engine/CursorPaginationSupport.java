package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;

import java.util.List;
import java.util.Map;

final class CursorPaginationSupport {

    private CursorPaginationSupport() {
    }

    static void applyCursorQuery(
            Map<String, String> query,
            RuntimeConnectorConfig.PaginationSettings pagination,
            String cursor,
            boolean firstPage
    ) {
        if (pagination.pageSizeParam() != null && !pagination.pageSizeParam().isBlank()) {
            query.put(pagination.pageSizeParam(), String.valueOf(pagination.pageSize()));
        }
        if (firstPage && pagination.firstPageOmitCursor()) {
            return;
        }
        if (pagination.cursorParam() == null || pagination.cursorParam().isBlank()) {
            throw new IllegalArgumentException("pagination.cursor_param is required for cursor strategy");
        }
        query.put(pagination.cursorParam(), cursor == null ? "" : cursor);
    }

    static boolean shouldStop(
            RuntimeConnectorConfig.PaginationSettings pagination,
            List<Object> pageRecords,
            String nextCursor,
            Boolean hasMore
    ) {
        List<String> rules = pagination.stopWhen() == null || pagination.stopWhen().isEmpty()
                ? RuntimeConnectorConfig.PaginationSettings.defaultStopWhen()
                : pagination.stopWhen();
        for (String rule : rules) {
            if ("empty_page".equalsIgnoreCase(rule) && pageRecords.isEmpty()) {
                return true;
            }
            if ("empty_cursor".equalsIgnoreCase(rule) && isBlank(nextCursor)) {
                return true;
            }
            if ("has_more_false".equalsIgnoreCase(rule) && Boolean.FALSE.equals(hasMore)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
