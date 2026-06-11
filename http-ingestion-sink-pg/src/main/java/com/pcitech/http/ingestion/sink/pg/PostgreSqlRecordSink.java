package com.pcitech.http.ingestion.sink.pg;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.sink.RecordSink;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PostgreSqlRecordSink implements RecordSink {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private volatile HikariDataSource dataSource;

    public PostgreSqlRecordSink(
            @Value("${ingestion.external-pg.url:}") String jdbcUrl,
            @Value("${ingestion.external-pg.username:}") String username,
            @Value("${ingestion.external-pg.password:}") String password
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean supportsType(String type) {
        return type == null || type.isBlank() || "postgresql".equalsIgnoreCase(type);
    }

    @Override
    public boolean isAvailable() {
        return jdbcUrl != null && !jdbcUrl.isBlank();
    }

    @Override
    public int write(List<Map<String, Object>> records, RuntimeConnectorConfig.SinkSettings sink) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        if (sink == null || sink.table() == null || sink.table().isBlank()) {
            throw new IllegalArgumentException("Sink table is required");
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());

        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            columns.addAll(record.keySet());
        }
        if (columns.isEmpty()) {
            return 0;
        }
        List<String> columnList = new ArrayList<>(columns);
        List<String> keyColumns = sink.keys() == null || sink.keys().isEmpty()
                ? List.of(columnList.get(0))
                : sink.keys();

        String sql = buildUpsertSql(sink.schema(), sink.table(), columnList, keyColumns, sink.writeMode());
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, Object> record : records) {
            Object[] values = columnList.stream()
                    .map(column -> toSqlValue(column, record.get(column)))
                    .toArray();
            batch.add(values);
        }
        jdbc.batchUpdate(sql, batch);
        return records.size();
    }

    private String buildUpsertSql(String schema, String table, List<String> columns, List<String> keys, String writeMode) {
        String qualified = quote(schema) + "." + quote(table);
        String columnNames = String.join(", ", columns.stream().map(this::quote).toList());
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        if (!"upsert".equalsIgnoreCase(writeMode)) {
            return "INSERT INTO " + qualified + " (" + columnNames + ") VALUES (" + placeholders + ")";
        }
        String conflict = String.join(", ", keys.stream().map(this::quote).toList());
        String updates = columns.stream()
                .filter(c -> !keys.contains(c))
                .map(c -> quote(c) + " = EXCLUDED." + quote(c))
                .reduce((a, b) -> a + ", " + b)
                .orElse(quote(keys.get(0)) + " = EXCLUDED." + quote(keys.get(0)));
        return "INSERT INTO " + qualified + " (" + columnNames + ") VALUES (" + placeholders + ") "
                + "ON CONFLICT (" + conflict + ") DO UPDATE SET " + updates;
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static Object toSqlValue(String column, Object value) {
        if (value instanceof String text && isJsonColumn(column) && !text.isBlank()) {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            try {
                jsonObject.setValue(text);
            } catch (SQLException ex) {
                throw new IllegalStateException("Invalid JSON for column " + column, ex);
            }
            return jsonObject;
        }
        return value;
    }

    private static boolean isJsonColumn(String column) {
        return "raw_json".equals(column) || column.endsWith("_json");
    }

    private HikariDataSource dataSource() {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(jdbcUrl);
                    config.setUsername(username);
                    config.setPassword(password);
                    config.setMaximumPoolSize(5);
                    dataSource = new HikariDataSource(config);
                }
            }
        }
        return dataSource;
    }
}
