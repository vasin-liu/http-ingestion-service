package com.pcitech.http.ingestion.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public final class PgTestSupport {

    private PgTestSupport() {
    }

    public static void createIntegrationTables(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS dahua_vehicle_pass");
            statement.execute("DROP TABLE IF EXISTS dahua_vehicle_count");
            statement.execute("DROP TABLE IF EXISTS dahua_motor_illegal");
            statement.execute("DROP TABLE IF EXISTS dahua_motor_illegal_count");
            statement.execute("DROP TABLE IF EXISTS meiya_traffic_police_alert");
            statement.execute("DROP TABLE IF EXISTS meiya_dispatch110_flow");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("DROP TABLE IF EXISTS jiadu_event_info");
            statement.execute("""
                    CREATE TABLE dahua_vehicle_pass (
                        record_id VARCHAR(64) PRIMARY KEY,
                        plate_num VARCHAR(32),
                        cap_time VARCHAR(32),
                        channel_name VARCHAR(255),
                        plate_type VARCHAR(16)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE dahua_vehicle_count (
                        stat_key VARCHAR(32) PRIMARY KEY,
                        total_count BIGINT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE dahua_motor_illegal (
                        record_id VARCHAR(64) PRIMARY KEY,
                        plate_num VARCHAR(32),
                        cap_time BIGINT,
                        rec_type BIGINT,
                        channel_name VARCHAR(255),
                        plate_type VARCHAR(16)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE dahua_motor_illegal_count (
                        stat_key VARCHAR(32) PRIMARY KEY,
                        total_count BIGINT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE meiya_traffic_police_alert (
                        jqbh VARCHAR(64) PRIMARY KEY,
                        jqfssj VARCHAR(32),
                        evcc VARCHAR(32),
                        desct VARCHAR(512)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE meiya_dispatch110_flow (
                        bh VARCHAR(64) PRIMARY KEY,
                        jjdbh VARCHAR(64),
                        gxsj VARCHAR(32),
                        xxlxms VARCHAR(512)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE users (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE jiadu_event_info (
                        event_id VARCHAR(64) PRIMARY KEY,
                        event_type BIGINT,
                        event_name VARCHAR(255),
                        send_time VARCHAR(32),
                        camera_id VARCHAR(64),
                        img_url TEXT,
                        video_url TEXT,
                        event_time VARCHAR(32),
                        confidence DOUBLE PRECISION,
                        task_id VARCHAR(64),
                        event_group INT,
                        census INT,
                        inter_day INT,
                        enter_number INT,
                        out_number INT,
                        raw_json JSONB
                    )
                    """);
        }
    }

    public static int countRows(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static String readString(
            Connection connection,
            String table,
            String keyColumn,
            String key,
            String column
    ) throws Exception {
        try (var ps = connection.prepareStatement(
                "SELECT \"" + column + "\" FROM " + table + " WHERE \"" + keyColumn + "\" = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    public static long readLong(
            Connection connection,
            String table,
            String keyColumn,
            String key,
            String column
    ) throws Exception {
        try (var ps = connection.prepareStatement(
                "SELECT \"" + column + "\" FROM " + table + " WHERE \"" + keyColumn + "\" = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }
}
