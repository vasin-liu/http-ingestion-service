package com.pcitech.http.ingestion.api.mock;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MockIntegrationSourceStore {

    private static final DateTimeFormatter DAHUA_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MEIYA_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private final CopyOnWriteArrayList<Map<String, Object>> dahuaVehicleRecords = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> dahuaMotorIllegalRecords = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> meiyaTrafficPoliceRecords = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> meiyaDispatch110Records = new CopyOnWriteArrayList<>();
    private volatile int dahuaVehicleCount = 0;

    public MockIntegrationSourceStore() {
        reset();
    }

    public void reset() {
        dahuaVehicleRecords.clear();
        dahuaMotorIllegalRecords.clear();
        meiyaTrafficPoliceRecords.clear();
        meiyaDispatch110Records.clear();
        int size = MockDataCatalog.DEFAULT_CATALOG_SIZE;
        dahuaVehicleRecords.addAll(MockDataCatalog.dahuaVehicles(size));
        dahuaMotorIllegalRecords.addAll(MockDataCatalog.dahuaMotorIllegal(size));
        meiyaTrafficPoliceRecords.addAll(MockDataCatalog.meiyaTrafficPolice(size));
        meiyaDispatch110Records.addAll(MockDataCatalog.meiyaDispatch110(size));
        dahuaVehicleCount = dahuaVehicleRecords.size();
    }

    public void setDahuaVehicleCount(int totalCount) {
        dahuaVehicleCount = totalCount;
    }

    public void addDahuaVehicle(Map<String, Object> record) {
        dahuaVehicleRecords.add(new LinkedHashMap<>(record));
        dahuaVehicleCount = dahuaVehicleRecords.size();
    }

    public void addDahuaMotorIllegal(Map<String, Object> record) {
        dahuaMotorIllegalRecords.add(new LinkedHashMap<>(record));
    }

    public int dahuaMotorIllegalCount() {
        return dahuaMotorIllegalRecords.size();
    }

    public void addMeiyaTrafficPolice(Map<String, Object> record) {
        meiyaTrafficPoliceRecords.add(new LinkedHashMap<>(record));
    }

    public void addMeiyaDispatch110(Map<String, Object> record) {
        meiyaDispatch110Records.add(new LinkedHashMap<>(record));
    }

    public int dahuaVehicleCount() {
        return dahuaVehicleRecords.size();
    }

    public int meiyaTrafficPoliceCount() {
        return meiyaTrafficPoliceRecords.size();
    }

    public int meiyaDispatch110Count() {
        return meiyaDispatch110Records.size();
    }

    public Map<String, Object> queryDahuaVehicles(
            Integer page,
            Integer pageSize,
            String startTime,
            String endTime
    ) {
        List<Map<String, Object>> filtered = dahuaVehicleRecords.stream()
                .filter(record -> matchesDahuaTime(record.get("capTime"), startTime, endTime))
                .toList();
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 100 : pageSize;
        int from = (safePage - 1) * safeSize;
        int to = Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> pageRecords = from >= filtered.size()
                ? List.of()
                : new ArrayList<>(filtered.subList(from, to));
        int nextPage = to >= filtered.size() ? -1 : safePage + 1;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", filtered.size());
        response.put("nextPage", nextPage);
        response.put("results", pageRecords);
        return response;
    }

    public Map<String, Object> countDahuaVehicles(String startTime, String endTime) {
        long total = dahuaVehicleRecords.stream()
                .filter(record -> matchesDahuaTime(record.get("capTime"), startTime, endTime))
                .count();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", (int) total);
        return response;
    }

    public Map<String, Object> queryDahuaMotorIllegal(
            Integer page,
            Integer pageSize,
            String startTimeUtc,
            String endTimeUtc,
            String startTimeStr,
            String endTimeStr
    ) {
        List<Map<String, Object>> filtered = dahuaMotorIllegalRecords.stream()
                .filter(record -> matchesMotorIllegalTime(
                        record.get("capTime"),
                        startTimeUtc,
                        endTimeUtc,
                        startTimeStr,
                        endTimeStr
                ))
                .toList();
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 100 : pageSize;
        int from = (safePage - 1) * safeSize;
        int to = Math.min(from + safeSize, filtered.size());
        List<Map<String, Object>> pageRecords = from >= filtered.size()
                ? List.of()
                : new ArrayList<>(filtered.subList(from, to));
        int nextPage = to >= filtered.size() ? -1 : safePage + 1;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", filtered.size());
        response.put("nextPage", nextPage);
        response.put("results", pageRecords);
        return response;
    }

    public Map<String, Object> countDahuaMotorIllegal(
            String startTimeUtc,
            String endTimeUtc,
            String startTimeStr,
            String endTimeStr
    ) {
        long total = dahuaMotorIllegalRecords.stream()
                .filter(record -> matchesMotorIllegalTime(
                        record.get("capTime"),
                        startTimeUtc,
                        endTimeUtc,
                        startTimeStr,
                        endTimeStr
                ))
                .count();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", (int) total);
        return response;
    }

    public Map<String, Object> queryMeiyaTrafficPolice(long skip, long limit, List<String> evccRange) {
        List<Map<String, Object>> filtered = meiyaTrafficPoliceRecords.stream()
                .filter(record -> matchesMeiyaRange(record.get("evcc"), evccRange))
                .toList();
        return pageMeiyaResponse(filtered, skip, limit);
    }

    public Map<String, Object> queryMeiyaDispatch110(long skip, long limit, List<String> gxsjRange) {
        List<Map<String, Object>> filtered = meiyaDispatch110Records.stream()
                .filter(record -> matchesMeiyaRange(record.get("gxsj"), gxsjRange))
                .toList();
        return pageMeiyaResponse(filtered, skip, limit);
    }

    private Map<String, Object> pageMeiyaResponse(List<Map<String, Object>> filtered, long skip, long limit) {
        long safeSkip = Math.max(0, skip);
        long safeLimit = limit <= 0 ? 100 : limit;
        int from = (int) Math.min(safeSkip, filtered.size());
        int to = (int) Math.min(from + safeLimit, filtered.size());
        List<Map<String, Object>> pageRecords = from >= filtered.size()
                ? List.of()
                : new ArrayList<>(filtered.subList(from, to));
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("total", (long) filtered.size());
        states.put("rows", pageRecords.size());
        states.put("cost", 1L);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("version", "2.0");
        response.put("code", 1);
        response.put("data", pageRecords);
        response.put("states", states);
        response.put("msg", "ok");
        return response;
    }

    private boolean matchesMotorIllegalTime(
            Object capTimeValue,
            String startTimeUtc,
            String endTimeUtc,
            String startTimeStr,
            String endTimeStr
    ) {
        Instant capTime = parseCapTimeInstant(capTimeValue);
        Instant start = parseDahuaInstant(startTimeUtc);
        if (start == null) {
            start = parseMeiyaInstant(startTimeStr);
        }
        Instant end = parseDahuaInstant(endTimeUtc);
        if (end == null) {
            end = parseMeiyaInstant(endTimeStr);
        }
        if (start != null && capTime != null && capTime.isBefore(start)) {
            return false;
        }
        if (end != null && capTime != null && capTime.isAfter(end)) {
            return false;
        }
        return true;
    }

    private Instant parseCapTimeInstant(Object value) {
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        return parseDahuaInstant(value);
    }

    private boolean matchesDahuaTime(Object capTimeValue, String startTime, String endTime) {
        Instant capTime = parseDahuaInstant(capTimeValue);
        Instant start = parseDahuaInstant(startTime);
        Instant end = parseDahuaInstant(endTime);
        if (start != null && capTime != null && capTime.isBefore(start)) {
            return false;
        }
        if (end != null && capTime != null && capTime.isAfter(end)) {
            return false;
        }
        return true;
    }

    private boolean matchesMeiyaRange(Object fieldValue, List<String> range) {
        if (range == null || range.isEmpty()) {
            return true;
        }
        Instant value = parseMeiyaInstant(fieldValue);
        Instant start = range.size() > 0 ? parseMeiyaInstant(range.get(0)) : null;
        Instant end = range.size() > 1 ? parseMeiyaInstant(range.get(1)) : null;
        if (start != null && value != null && value.isBefore(start)) {
            return false;
        }
        if (end != null && value != null && value.isAfter(end)) {
            return false;
        }
        return true;
    }

    private Instant parseDahuaInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return DAHUA_UTC.parse(String.valueOf(value), Instant::from);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Instant parseMeiyaInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MEIYA_DATETIME.parse(String.valueOf(value), Instant::from);
        } catch (DateTimeParseException ex) {
            try {
                return Instant.parse(String.valueOf(value));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static List<Map<String, Object>> defaultDahuaVehicles() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(dahuaVehicle("rec-001", "粤A12345", "20250601T080000Z", "卡口A"));
        records.add(dahuaVehicle("rec-002", "粤B67890", "20250601T090000Z", "卡口B"));
        return records;
    }

    private static List<Map<String, Object>> defaultMeiyaTrafficPolice() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(meiyaTrafficPolice("jq-001", "2025-06-01 08:00:00", "2025-06-01 08:05:00", "交通拥堵"));
        records.add(meiyaTrafficPolice("jq-002", "2025-06-01 09:00:00", "2025-06-01 09:10:00", "事故处理"));
        return records;
    }

    private static List<Map<String, Object>> defaultMeiyaDispatch110() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(meiyaDispatch110("bh-001", "jjd-001", "2025-06-01 08:01:00", "接警内容1"));
        records.add(meiyaDispatch110("bh-002", "jjd-002", "2025-06-01 09:01:00", "接警内容2"));
        return records;
    }

    private static List<Map<String, Object>> defaultDahuaMotorIllegal() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(dahuaMotorIllegal(
                "ill-001",
                "粤A12345",
                Instant.parse("2025-06-01T08:00:00Z"),
                1301,
                "违法卡口A",
                "02"
        ));
        records.add(dahuaMotorIllegal(
                "ill-002",
                "粤B67890",
                Instant.parse("2025-06-01T09:00:00Z"),
                1302,
                "违法卡口B",
                "02"
        ));
        return records;
    }

    private static Map<String, Object> dahuaMotorIllegal(
            String recordId,
            String plateNum,
            Instant capTime,
            int recType,
            String channelName,
            String plateType
    ) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordId", recordId);
        record.put("plateNum", plateNum);
        record.put("capTime", capTime.toEpochMilli());
        record.put("capTimeStr", MEIYA_DATETIME.format(capTime));
        record.put("recType", recType);
        record.put("channelName", channelName);
        record.put("plateType", plateType);
        return record;
    }

    private static Map<String, Object> dahuaVehicle(String recordId, String plateNum, String capTime, String channelName) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordId", recordId);
        record.put("plateNum", plateNum);
        record.put("capTime", capTime);
        record.put("channelName", channelName);
        record.put("plateType", "02");
        return record;
    }

    private static Map<String, Object> meiyaTrafficPolice(String jqbh, String jqfssj, String evcc, String desct) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("jqbh", jqbh);
        record.put("jqfssj", jqfssj);
        record.put("evcc", evcc);
        record.put("desct", desct);
        return record;
    }

    private static Map<String, Object> meiyaDispatch110(String bh, String jjdbh, String gxsj, String xxlxms) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("bh", bh);
        record.put("jjdbh", jjdbh);
        record.put("gxsj", gxsj);
        record.put("xxlxms", xxlxms);
        return record;
    }
}
