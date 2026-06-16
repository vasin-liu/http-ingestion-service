package com.pcitech.http.ingestion.core.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LinkHeaderSupportTest {

    @Test
    void parseRelLink_findsNext() {
        Optional<String> url = LinkHeaderSupport.parseRelLink(
                "<http://api.example/items?page=2>; rel=\"next\", <http://api.example/items?page=1>; rel=\"prev\"",
                "next"
        );
        assertThat(url).contains("http://api.example/items?page=2");
    }

    @Test
    void parseRelLink_missingRel() {
        assertThat(LinkHeaderSupport.parseRelLink(
                "<http://api.example/items?page=1>; rel=\"prev\"", "next")).isEmpty();
    }

    @Test
    void shouldStop_noNextLink() {
        var pagination = new com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig.PaginationSettings(
                "link_header", "query", "page_number", "page", "page_size", 1, 100, null, 1000,
                "none", null, null, true,
                null, null, null, true,
                List.of("no_next_link", "empty_page"), "Link", "next"
        );
        assertThat(LinkHeaderSupport.shouldStop(
                pagination, List.of(Map.of("id", 1)), Optional.empty())).isTrue();
    }
}
