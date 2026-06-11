package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPaginationSupportTest {

    @Test
    void shouldStop_emptyCursor() {
        var pagination = cursorSettings(List.of("empty_cursor"));
        assertThat(CursorPaginationSupport.shouldStop(
                pagination, List.of(Map.of("id", 1)), "", null)).isTrue();
    }

    @Test
    void shouldStop_hasMoreFalse() {
        var pagination = cursorSettings(List.of("has_more_false"));
        assertThat(CursorPaginationSupport.shouldStop(
                pagination, List.of(Map.of("id", 1)), "next", false)).isTrue();
    }

    @Test
    void shouldStop_emptyPage() {
        var pagination = cursorSettings(List.of("empty_page"));
        assertThat(CursorPaginationSupport.shouldStop(
                pagination, List.of(), "next", true)).isTrue();
    }

    @Test
    void shouldStop_doesNotStopWhenNextCursorPresent() {
        var pagination = cursorSettings(List.of("empty_cursor", "empty_page"));
        assertThat(CursorPaginationSupport.shouldStop(
                pagination, List.of(Map.of("id", 1)), "next-page", true)).isFalse();
    }

    private static RuntimeConnectorConfig.PaginationSettings cursorSettings(List<String> stopWhen) {
        return new RuntimeConnectorConfig.PaginationSettings(
                "cursor", "query", "page_number", "page", "limit", 0, 10, null, 100,
                "none", null, null, true,
                "cursor", "$.meta.next", "$.meta.hasMore", true, stopWhen
        );
    }
}
