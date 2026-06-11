package com.pcitech.http.ingestion.sink.pg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlRecordSinkTestcontainersTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void setUp() throws Exception {
        PostgreSqlSinkTestSupport.resetUsersTable(POSTGRES);
    }

    @Test
    void upsert_insertsRows() throws Exception {
        PostgreSqlSinkTestSupport.assertUpsertInsert(POSTGRES);
    }

    @Test
    void upsert_updatesExistingKeys() throws Exception {
        PostgreSqlSinkTestSupport.assertUpsertUpdate(POSTGRES);
    }
}
