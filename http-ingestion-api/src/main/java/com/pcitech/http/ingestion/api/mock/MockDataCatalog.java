package com.pcitech.http.ingestion.api.mock;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates large deterministic mock datasets for integration / E2E tests.
 */
public final class MockDataCatalog {

    public static final int DEFAULT_CATALOG_SIZE = Integer.parseInt(
            System.getProperty("mock.catalog.size", "1000")
    );

    private static final DateTimeFormatter DAHUA_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MEIYA_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private MockDataCatalog() {
    }

    public static List<Map<String, Object>> dahuaVehicles(int size) {
        List<Map<String, Object>> records = new ArrayList<>(size);
        Instant base = Instant.parse("2025-06-01T00:00:00Z");
        for (int i = 0; i < size; i++) {
            Instant capTime = base.plusSeconds(i * 37L);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("recordId", "rec-" + String.format("%05d", i + 1));
            record.put("plateNum", "粤" + (char) ('A' + (i % 26)) + String.format("%05d", i + 1));
            record.put("capTime", DAHUA_UTC.format(capTime));
            record.put("channelName", "卡口-" + (i % 50 + 1));
            record.put("plateType", "02");
            records.add(record);
        }
        return records;
    }

    public static List<Map<String, Object>> dahuaMotorIllegal(int size) {
        List<Map<String, Object>> records = new ArrayList<>(size);
        Instant base = Instant.parse("2025-06-01T00:00:00Z");
        for (int i = 0; i < size; i++) {
            Instant capTime = base.plusSeconds(i * 41L);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("recordId", "ill-" + String.format("%05d", i + 1));
            record.put("plateNum", "粤" + (char) ('A' + (i % 26)) + String.format("%05d", i + 1));
            record.put("capTime", capTime.toEpochMilli());
            record.put("capTimeStr", MEIYA_DATETIME.format(capTime));
            record.put("recType", 1301 + (i % 5));
            record.put("channelName", "违法卡口-" + (i % 40 + 1));
            record.put("plateType", "02");
            records.add(record);
        }
        return records;
    }

    public static List<Map<String, Object>> meiyaTrafficPolice(int size) {
        List<Map<String, Object>> records = new ArrayList<>(size);
        Instant base = Instant.parse("2025-06-01T00:00:00Z");
        for (int i = 0; i < size; i++) {
            Instant eventTime = base.plusSeconds(i * 29L);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("jqbh", "jq-" + String.format("%05d", i + 1));
            record.put("jqfssj", MEIYA_DATETIME.format(eventTime));
            record.put("evcc", MEIYA_DATETIME.format(eventTime.plusSeconds(120)));
            record.put("desct", "警情描述-" + (i + 1));
            records.add(record);
        }
        return records;
    }

    public static List<Map<String, Object>> meiyaDispatch110(int size) {
        List<Map<String, Object>> records = new ArrayList<>(size);
        Instant base = Instant.parse("2025-06-01T00:00:00Z");
        for (int i = 0; i < size; i++) {
            Instant eventTime = base.plusSeconds(i * 31L);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("bh", "bh-" + String.format("%05d", i + 1));
            record.put("jjdbh", "jjd-" + String.format("%05d", i + 1));
            record.put("gxsj", MEIYA_DATETIME.format(eventTime));
            record.put("xxlxms", "110流水-" + (i + 1));
            records.add(record);
        }
        return records;
    }
}
