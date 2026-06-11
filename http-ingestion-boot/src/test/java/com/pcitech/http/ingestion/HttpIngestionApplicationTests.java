package com.pcitech.http.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class HttpIngestionApplicationTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ingestion-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @Test
    void contextLoads() {
    }
}
