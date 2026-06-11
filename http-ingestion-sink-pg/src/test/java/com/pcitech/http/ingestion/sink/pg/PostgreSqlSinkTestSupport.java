package com.pcitech.http.ingestion.sink.pg;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class PostgreSqlSinkTestSupport {

    private PostgreSqlSinkTestSupport() {
    }

    static void resetUsersTable(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255))");
        }
    }

    static void assertUpsertInsert(PostgreSQLContainer<?> postgres) throws Exception {
        PostgreSqlRecordSink sink = new PostgreSqlRecordSink(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        RuntimeConnectorConfig.SinkSettings sinkSettings = new RuntimeConnectorConfig.SinkSettings(
                "postgresql", "public", "users", List.of("id"), "upsert", 500, null, "json"
        );

        int written = sink.write(List.of(
                Map.of("id", 1L, "name", "Alice"),
                Map.of("id", 2L, "name", "Bob")
        ), sinkSettings);

        assertThat(written).isEqualTo(2);
        assertThat(countRows(postgres)).isEqualTo(2);
        assertThat(readName(postgres, 1L)).isEqualTo("Alice");
    }

    static void assertUpsertUpdate(PostgreSQLContainer<?> postgres) throws Exception {
        PostgreSqlRecordSink sink = new PostgreSqlRecordSink(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        RuntimeConnectorConfig.SinkSettings sinkSettings = new RuntimeConnectorConfig.SinkSettings(
                "postgresql", "public", "users", List.of("id"), "upsert", 500, null, "json"
        );

        sink.write(List.of(Map.of("id", 1L, "name", "Alice")), sinkSettings);
        sink.write(List.of(Map.of("id", 1L, "name", "Alice Updated")), sinkSettings);

        assertThat(countRows(postgres)).isEqualTo(1);
        assertThat(readName(postgres, 1L)).isEqualTo("Alice Updated");
    }

    private static int countRows(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String readName(PostgreSQLContainer<?> postgres, long id) throws Exception {
        try (Connection connection = postgres.createConnection("");
             var ps = connection.prepareStatement("SELECT name FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }
}
