package com.pcitech.http.ingestion.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathSupportCursorTest {

    private JsonPathSupport support;

    @BeforeEach
    void setUp() {
        support = new JsonPathSupport(new ObjectMapper());
    }

    @Test
    void readStringAndBoolean_fromResponse() {
        String body = "{\"meta\":{\"next\":\"abc\",\"hasMore\":false}}";
        assertThat(support.readString(body, "$.meta.next")).isEqualTo("abc");
        assertThat(support.readBoolean(body, "$.meta.hasMore")).isFalse();
    }
}
