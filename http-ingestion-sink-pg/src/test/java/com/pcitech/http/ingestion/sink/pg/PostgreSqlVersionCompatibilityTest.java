package com.pcitech.http.ingestion.sink.pg;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlVersionCompatibilityTest {

    @ParameterizedTest(name = "PostgreSQL {0}.x upsert insert")
    @ValueSource(strings = {"10", "11", "12", "13", "14", "15"})
    void upsertInsertsRowsOnSupportedVersions(String majorVersion) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:" + majorVersion)) {
            postgres.start();
            PostgreSqlSinkTestSupport.resetUsersTable(postgres);
            PostgreSqlSinkTestSupport.assertUpsertInsert(postgres);
        }
    }

    @ParameterizedTest(name = "PostgreSQL {0}.x upsert update")
    @ValueSource(strings = {"10", "11", "12", "13", "14", "15"})
    void upsertUpdatesExistingKeysOnSupportedVersions(String majorVersion) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:" + majorVersion)) {
            postgres.start();
            PostgreSqlSinkTestSupport.resetUsersTable(postgres);
            PostgreSqlSinkTestSupport.assertUpsertUpdate(postgres);
        }
    }
}
