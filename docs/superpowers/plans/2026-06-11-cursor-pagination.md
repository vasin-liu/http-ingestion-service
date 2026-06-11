# Cursor 分页策略 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 `pagination.strategy: cursor`，支持 Query/Body 传参、三种 `stop_when` 终止条件、向导 UI、`rest-cursor` 示例模板及三层测试。

**Architecture:** 扩展 `PaginationSettings` 与 `RuntimeConfigParser`；`HttpPullEngine` 在 `strategy=cursor` 时走独立翻页循环（可抽 `CursorPaginationSupport` helper）；`RequestBodyComposer` 增加 body cursor 注入；Admin UI 分页步骤按策略切换字段；Mock + WireMock + Playwright 覆盖主路径。

**Tech Stack:** Java 21, Spring Boot 3.4, Jayway JsonPath, React/Ant Design, WireMock, Playwright, Podman/Testcontainers

**Spec:** `docs/superpowers/specs/2026-06-11-cursor-pagination-design.md`

---

## File Structure

| 路径 | 职责 |
|------|------|
| `http-ingestion-core/.../RuntimeConnectorConfig.java` | `PaginationSettings` 新增 cursor 字段 |
| `http-ingestion-core/.../RuntimeConfigParser.java` | 解析 cursor 配置 |
| `http-ingestion-core/.../JsonPathSupport.java` | `readString` / `readBoolean` |
| `http-ingestion-core/.../CursorPaginationSupport.java` | **新建** — stop 判断、query cursor 注入 |
| `http-ingestion-core/.../HttpPullEngine.java` | `pullWithCursor` 分支 |
| `http-ingestion-core/.../RequestBodyComposer.java` | body cursor + page_size |
| `http-ingestion-core/.../ConnectorTemplateService.java` | `rest-cursor` 模板 |
| `http-ingestion-core/.../CursorPaginationSupportTest.java` | **新建** — 单元测试 |
| `http-ingestion-core/.../RequestBodyComposerTest.java` | body cursor 用例 |
| `http-ingestion-api/.../MockE2eRestSourceController.java` | GET cursor mock |
| `http-ingestion-boot/.../ConnectorConfigFactory.java` | `cursorItemsConfig` |
| `http-ingestion-boot/.../HttpIngestionE2ETest.java` | `CursorPull` nested |
| `http-ingestion-admin-ui/src/api/client.ts` | `defaultConfig.pagination` cursor 字段 |
| `http-ingestion-admin-ui/src/pages/ConnectorWizardPage.tsx` | 策略切换 UI |
| `http-ingestion-admin-ui/src/i18n/locales/zh-CN.ts` | 中文文案 |
| `http-ingestion-admin-ui/src/i18n/locales/en-US.ts` | 英文文案 |
| `http-ingestion-admin-ui/src/i18n/types.ts` | 类型定义 |
| `http-ingestion-admin-ui/e2e/cursor-pagination.spec.ts` | **新建** — UI E2E |
| `http-ingestion-admin-ui/e2e/connector-list.spec.ts` | 模板列表含 `rest-cursor` |
| `docs/testing/test-matrix.md` | 矩阵更新 |
| `README.md` | 示例模板数量 4→5 |

---

## Task 1: 配置模型与解析器

**Files:**
- Modify: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/config/runtime/RuntimeConnectorConfig.java`
- Modify: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/config/runtime/RuntimeConfigParser.java`
- Test: `http-ingestion-core/src/test/java/com/pcitech/http/ingestion/core/config/RuntimeConfigParserCursorTest.java`（新建）

- [ ] **Step 1: 扩展 `PaginationSettings` record**

在 `RuntimeConnectorConfig.PaginationSettings` 末尾追加字段（保持现有字段顺序不变，新字段放最后）：

```java
public record PaginationSettings(
        String strategy,
        String location,
        String pageValueType,
        String pageParam,
        String pageSizeParam,
        int pageStart,
        int pageSize,
        String totalCountPath,
        int maxPages,
        String totalCountSource,
        String totalCountUrl,
        String totalCountMethod,
        boolean totalCountReuseBody,
        // cursor fields (ignored when strategy != cursor)
        String cursorParam,
        String cursorResponsePath,
        String hasMorePath,
        boolean firstPageOmitCursor,
        List<String> stopWhen
) {
    public static PaginationSettings defaults() {
        return new PaginationSettings(
                "page_page_size", "query", "page_number", "page", "page_size", 1, 100, null, 1000,
                "none", null, null, true,
                null, null, null, true, List.of("empty_cursor", "empty_page")
        );
    }

    public static List<String> defaultStopWhen() {
        return List.of("empty_cursor", "empty_page");
    }
}
```

- [ ] **Step 2: 更新 `RuntimeConfigParser.parsePagination`**

在 `parsePagination` 返回处追加 cursor 字段解析：

```java
String strategy = text(node, "strategy", "page_page_size");
List<String> stopWhen = new ArrayList<>();
JsonNode stopNode = node.path("stop_when");
if (stopNode.isArray()) {
    stopNode.forEach(item -> {
        if (item.isTextual() && !item.asText().isBlank()) {
            stopWhen.add(item.asText().trim());
        }
    });
}
if (stopWhen.isEmpty()) {
    stopWhen = RuntimeConnectorConfig.PaginationSettings.defaultStopWhen();
}
return new RuntimeConnectorConfig.PaginationSettings(
        strategy,
        text(node, "location", "query"),
        text(node, "page_value_type", "page_number"),
        text(node, "cursor_param", text(node, "page_param", "page")),
        text(node, "page_size_param", "page_size"),
        node.path("page_start").asInt(1),
        node.path("page_size").asInt(100),
        totalPath,
        node.path("max_pages").asInt(1000),
        totalSource,
        countUrl,
        countMethod,
        reuseBody,
        text(node, "cursor_param", null),
        text(node, "cursor_response_path", null),
        text(node, "has_more_path", null),
        !node.has("first_page_omit_cursor") || node.path("first_page_omit_cursor").asBoolean(true),
        stopWhen
);
```

> 注意：`page_param` 与 `cursor_param` 解析：`cursor_param` 优先读 `cursor_param` 键；`page_param` 仍用于 page/offset 策略。上面示例中第二个 `text(node, "page_param", "page")` 应改为 `text(node, "page_param", strategy.equals("cursor") ? "cursor" : "page")` — 实现时分开变量：

```java
String cursorParam = text(node, "cursor_param", null);
String pageParam = text(node, "page_param", "page");
// record 第 4 参数用 pageParam；cursor 字段用 cursorParam
```

- [ ] **Step 3: 写解析器测试**

创建 `RuntimeConfigParserCursorTest.java`：

```java
@Test
void parseCursorPagination_queryWithStopWhen() throws Exception {
    String json = """
        {"pagination":{"strategy":"cursor","location":"query","cursor_param":"pageToken",
        "cursor_response_path":"$.meta.next","has_more_path":"$.meta.hasMore",
        "stop_when":["empty_cursor","has_more_false","empty_page"],"page_size":50}}
        """;
    RuntimeConnectorConfig config = RuntimeConfigParser.parse(objectMapper.readTree(json));
    var p = config.pagination();
    assertThat(p.strategy()).isEqualTo("cursor");
    assertThat(p.cursorParam()).isEqualTo("pageToken");
    assertThat(p.cursorResponsePath()).isEqualTo("$.meta.next");
    assertThat(p.stopWhen()).containsExactly("empty_cursor", "has_more_false", "empty_page");
}
```

- [ ] **Step 4: 修复编译 — 更新所有 `PaginationSettings` 构造调用**

```bash
rg "new RuntimeConnectorConfig.PaginationSettings" http-ingestion-core -l
```

更新 `RequestBodyComposerTest.java` 每个构造器末尾追加：`null, null, null, true, List.of("empty_cursor", "empty_page")`

- [ ] **Step 5: 运行测试**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-core test "-Dtest=RuntimeConfigParserCursorTest,RequestBodyComposerTest"
```

Expected: PASS

---

## Task 2: JsonPath 读取辅助

**Files:**
- Modify: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/engine/JsonPathSupport.java`
- Test: `http-ingestion-core/src/test/java/com/pcitech/http/ingestion/core/engine/JsonPathSupportCursorTest.java`（新建）

- [ ] **Step 1: 添加 `readString` 与 `readBoolean`**

```java
public String readString(String body, String path) {
    if (path == null || path.isBlank()) {
        return null;
    }
    DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
    Object value = context.read(path);
    if (value == null) {
        return null;
    }
    return String.valueOf(value);
}

public Boolean readBoolean(String body, String path) {
    if (path == null || path.isBlank()) {
        return null;
    }
    DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
    Object value = context.read(path);
    if (value == null) {
        return null;
    }
    if (value instanceof Boolean bool) {
        return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
}
```

- [ ] **Step 2: 单元测试**

```java
@Test
void readStringAndBoolean_fromResponse() {
    String body = "{\"meta\":{\"next\":\"abc\",\"hasMore\":false}}";
    assertThat(support.readString(body, "$.meta.next")).isEqualTo("abc");
    assertThat(support.readBoolean(body, "$.meta.hasMore")).isFalse();
}
```

- [ ] **Step 3: 运行**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-core test "-Dtest=JsonPathSupportCursorTest"
```

---

## Task 3: Cursor 翻页引擎

**Files:**
- Create: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/engine/CursorPaginationSupport.java`
- Modify: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/engine/HttpPullEngine.java`
- Test: `http-ingestion-core/src/test/java/com/pcitech/http/ingestion/core/engine/CursorPaginationSupportTest.java`

- [ ] **Step 1: 实现 `CursorPaginationSupport`**

```java
final class CursorPaginationSupport {

    private CursorPaginationSupport() {}

    static void applyCursorQuery(
            Map<String, String> query,
            RuntimeConnectorConfig.PaginationSettings pagination,
            String cursor,
            boolean firstPage
    ) {
        if (pagination.pageSizeParam() != null && !pagination.pageSizeParam().isBlank()) {
            query.put(pagination.pageSizeParam(), String.valueOf(pagination.pageSize()));
        }
        if (firstPage && pagination.firstPageOmitCursor()) {
            return;
        }
        if (pagination.cursorParam() == null || pagination.cursorParam().isBlank()) {
            throw new IllegalArgumentException("pagination.cursor_param is required for cursor strategy");
        }
        query.put(pagination.cursorParam(), cursor == null ? "" : cursor);
    }

    static boolean shouldStop(
            RuntimeConnectorConfig.PaginationSettings pagination,
            List<Object> pageRecords,
            String nextCursor,
            Boolean hasMore
    ) {
        List<String> rules = pagination.stopWhen() == null
                ? RuntimeConnectorConfig.PaginationSettings.defaultStopWhen()
                : pagination.stopWhen();
        for (String rule : rules) {
            if ("empty_page".equalsIgnoreCase(rule) && pageRecords.isEmpty()) {
                return true;
            }
            if ("empty_cursor".equalsIgnoreCase(rule) && isBlank(nextCursor)) {
                return true;
            }
            if ("has_more_false".equalsIgnoreCase(rule) && Boolean.FALSE.equals(hasMore)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

- [ ] **Step 2: `CursorPaginationSupportTest` — stop_when OR 语义**

```java
@Test
void shouldStop_emptyCursor() {
    var p = cursorSettings(List.of("empty_cursor"));
    assertThat(CursorPaginationSupport.shouldStop(p, List.of(Map.of("id", 1)), "", null)).isTrue();
}

@Test
void shouldStop_hasMoreFalse() {
    var p = cursorSettings(List.of("has_more_false"));
    assertThat(CursorPaginationSupport.shouldStop(p, List.of(Map.of("id", 1)), "next", false)).isTrue();
}

@Test
void shouldStop_emptyPage() {
    var p = cursorSettings(List.of("empty_page"));
    assertThat(CursorPaginationSupport.shouldStop(p, List.of(), "next", true)).isTrue();
}
```

- [ ] **Step 3: `HttpPullEngine` 增加 cursor 分支**

在 `pull(...)` 方法开头，`PaginationSettings pagination` 解析后插入：

```java
if ("cursor".equalsIgnoreCase(pagination.strategy())) {
    return pullWithCursor(config, watermark, incrementalMode, maxRecords, listener, pagination);
}
```

新增 `pullWithCursor` 方法（伪代码结构，实现时复制 `pull` 循环骨架）：

```java
private PullResult pullWithCursor(
        RuntimeConnectorConfig config,
        Instant watermark,
        boolean incrementalMode,
        Integer maxRecords,
        PullProgressListener listener,
        RuntimeConnectorConfig.PaginationSettings pagination
) {
    RuntimeConnectorConfig.HttpSettings http = config.http();
    List<Object> allRecords = new ArrayList<>();
    Instant maxTimestamp = watermark;
    String cursor = null;
    boolean firstPage = true;

    for (int pageIndex = 0; pageIndex < pagination.maxPages(); pageIndex++) {
        Map<String, String> query = new HashMap<>(http.query());
        String requestBody = null;

        if ("body".equalsIgnoreCase(pagination.location())) {
            requestBody = RequestBodyComposer.composeWithCursor(
                    objectMapper, http.bodyJson(), pagination, config.incremental(),
                    cursor, firstPage, watermark, incrementalMode
            );
        } else {
            CursorPaginationSupport.applyCursorQuery(query, pagination, cursor, firstPage);
            applyIncremental(query, config.incremental(), watermark, incrementalMode);
            if (http.bodyJson() != null && !http.bodyJson().isBlank()) {
                requestBody = RequestBodyComposer.composeWithCursor(
                        objectMapper, http.bodyJson(), pagination, config.incremental(),
                        cursor, firstPage, watermark, incrementalMode
                );
            }
        }

        TrialResponseDto response = trialRequestService.execute(
                HttpRequestAssembler.fromSettings(http, query, requestBody)
        );
        // ... 同现有 error/status 检查 ...

        List<Object> pageRecords = jsonPathSupport.readRecords(
                response.body(),
                config.transform() == null ? "$" : config.transform().inputRoot()
        );
        if (listener != null) {
            listener.onPage(pageIndex, pageRecords.size(), response.durationMs());
        }

        String nextCursor = jsonPathSupport.readString(response.body(), pagination.cursorResponsePath());
        Boolean hasMore = jsonPathSupport.readBoolean(response.body(), pagination.hasMorePath());

        if (CursorPaginationSupport.shouldStop(pagination, pageRecords, nextCursor, hasMore)) {
            allRecords.addAll(pageRecords);
            // 仍处理本页 records 与 timestamp（若未 empty_page 触发）
            break;
        }

        for (Object record : pageRecords) {
            allRecords.add(record);
            if (maxRecords != null && allRecords.size() >= maxRecords) {
                break;
            }
        }
        if (maxRecords != null && allRecords.size() >= maxRecords) {
            break;
        }

        // timestamp 增量更新（同现有）
        if (config.incremental() != null && config.incremental().enabled()) {
            for (Object record : pageRecords) {
                Instant ts = jsonPathSupport.readInstant(record, config.incremental().responsePath());
                if (ts != null && (maxTimestamp == null || ts.isAfter(maxTimestamp))) {
                    maxTimestamp = ts;
                }
            }
        }

        if (isBlank(nextCursor)) {
            break;
        }
        cursor = nextCursor;
        firstPage = false;
    }

    if (incrementalMode && config.incremental() != null && config.incremental().enabled()
            && (maxTimestamp == null || watermark == null || !maxTimestamp.isAfter(watermark))) {
        maxTimestamp = Instant.now();
    }
    return new PullResult(allRecords, maxTimestamp);
}
```

> **实现注意：** `shouldStop` 在追加 records **之前**判断时，若仅 `empty_cursor` 触发且本页有数据，应先 `addAll` 再 break。调整顺序：先收集 records → 更新 watermark → 再 `shouldStop` → break 或 `cursor = nextCursor`。

- [ ] **Step 4: 运行 core 测试**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-core test
```

---

## Task 4: RequestBodyComposer cursor 支持

**Files:**
- Modify: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/engine/RequestBodyComposer.java`
- Modify: `http-ingestion-core/src/test/java/com/pcitech/http/ingestion/core/engine/RequestBodyComposerTest.java`

- [ ] **Step 1: 新增 `composeWithCursor`**

```java
public static String composeWithCursor(
        ObjectMapper mapper,
        String bodyTemplate,
        RuntimeConnectorConfig.PaginationSettings pagination,
        RuntimeConnectorConfig.IncrementalSettings incremental,
        String cursor,
        boolean firstPage,
        Instant watermark,
        boolean incrementalMode
) {
    try {
        ObjectNode root = parseBody(mapper, bodyTemplate);
        if ("body".equalsIgnoreCase(pagination.location())) {
            applyBodyCursor(root, pagination, cursor, firstPage);
        }
        if (incrementalMode && incremental != null && incremental.enabled()
                && "body".equalsIgnoreCase(incremental.requestTarget())) {
            applyBodyIncremental(root, incremental, watermark);
        }
        return mapper.writeValueAsString(root);
    } catch (Exception ex) {
        throw new IllegalArgumentException("Failed to compose HTTP request body: " + ex.getMessage(), ex);
    }
}

private static void applyBodyCursor(
        ObjectNode root,
        RuntimeConnectorConfig.PaginationSettings pagination,
        String cursor,
        boolean firstPage
) {
    if (pagination.pageSizeParam() != null && !pagination.pageSizeParam().isBlank()) {
        setByPath(root, pagination.pageSizeParam(),
                JsonNodeFactory.instance.numberNode(pagination.pageSize()));
    }
    if (firstPage && pagination.firstPageOmitCursor()) {
        return;
    }
    setByPath(root, pagination.cursorParam(),
            TextNode.valueOf(cursor == null ? "" : cursor));
}
```

- [ ] **Step 2: 测试 — 首页 omit + 次页注入**

```java
@Test
void composeWithCursor_omitFirstPage_injectSecond() throws Exception {
    var pagination = new RuntimeConnectorConfig.PaginationSettings(
            "cursor", "body", "page_number", "page", "limit", 0, 10, null, 100,
            "none", null, null, true,
            "pageToken", "$.meta.next", null, true, List.of("empty_cursor", "empty_page")
    );
    String template = "{\"pageToken\":\"\",\"limit\":100}";
    String first = RequestBodyComposer.composeWithCursor(
            objectMapper, template, pagination, RuntimeConnectorConfig.IncrementalSettings.disabled(),
            null, true, null, false
    );
    assertThat(objectMapper.readTree(first).path("pageToken").isMissingNode()
            || objectMapper.readTree(first).path("pageToken").asText().isEmpty()).isTrue();

    String second = RequestBodyComposer.composeWithCursor(
            objectMapper, template, pagination, RuntimeConnectorConfig.IncrementalSettings.disabled(),
            "tok-2", false, null, false
    );
    assertThat(objectMapper.readTree(second).path("pageToken").asText()).isEqualTo("tok-2");
}
```

- [ ] **Step 3: 运行**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-core test "-Dtest=RequestBodyComposerTest,CursorPaginationSupportTest"
```

---

## Task 5: 示例模板与 Mock API

**Files:**
- Modify: `http-ingestion-core/src/main/java/com/pcitech/http/ingestion/core/service/ConnectorTemplateService.java`
- Modify: `http-ingestion-api/src/main/java/com/pcitech/http/ingestion/api/mock/MockE2eRestSourceController.java`

- [ ] **Step 1: 注册 `rest-cursor` 模板**

在 `ConnectorTemplateService` 构造函数 `templates` 列表追加 `restCursorTemplate()`：

```java
private ConnectorTemplateDto restCursorTemplate() {
    JsonNode config = objectMapper.valueToTree(Map.of(
            "http", Map.of(
                    "method", "GET",
                    "url", "https://api.example.com/items",
                    "headers", Map.of(),
                    "query", Map.of(),
                    "timeout_ms", 30000
            ),
            "pagination", Map.of(
                    "strategy", "cursor",
                    "location", "query",
                    "cursor_param", "cursor",
                    "page_size_param", "limit",
                    "page_size", 100,
                    "cursor_response_path", "$.meta.nextCursor",
                    "has_more_path", "$.meta.hasMore",
                    "first_page_omit_cursor", true,
                    "stop_when", List.of("empty_cursor", "has_more_false", "empty_page"),
                    "max_pages", 1000
            ),
            "incremental", Map.of("enabled", false),
            "sync", Map.of("on_first_run", "full"),
            "transform", Map.of(
                    "input_root", "$.data",
                    "steps", List.of(Map.of(
                            "type", "map_fields",
                            "mappings", List.of(
                                    Map.of("target", "id", "source", "$.id", "type", "long"),
                                    Map.of("target", "name", "source", "$.name", "type", "string")
                            )
                    ))
            ),
            "sink", Map.of(
                    "type", "postgresql",
                    "target", Map.of("schema", "public", "table", "items"),
                    "keys", List.of("id"),
                    "write_mode", "upsert",
                    "batch_size", 500
            ),
            "schedule", Map.of("enabled", true, "type", "cron", "expression", "0 0/5 * * * ?")
    ));
    return example("rest-cursor", "REST Cursor 分页示例",
            "演示 cursor/token 翻页与 hasMore 终止", "pull", config);
}
```

- [ ] **Step 2: Mock GET `/mock/e2e/cursor-items`**

```java
@GetMapping("/cursor-items")
public Map<String, Object> cursorItems(@RequestParam(required = false) String cursor) {
    if (cursor == null || cursor.isBlank()) {
        return Map.of(
                "data", List.of(Map.of("id", 1, "name", "Alice"), Map.of("id", 2, "name", "Bob")),
                "meta", Map.of("nextCursor", "page2", "hasMore", true)
        );
    }
    if ("page2".equals(cursor)) {
        return Map.of(
                "data", List.of(Map.of("id", 3, "name", "Carol")),
                "meta", Map.of("nextCursor", "", "hasMore", false)
        );
    }
    return Map.of("data", List.of(), "meta", Map.of("nextCursor", "", "hasMore", false));
}
```

- [ ] **Step 3: 编译验证**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-api -am compile -q
```

---

## Task 6: Java E2E（WireMock）

**Files:**
- Modify: `http-ingestion-boot/src/test/java/com/pcitech/http/ingestion/support/ConnectorConfigFactory.java`
- Modify: `http-ingestion-boot/src/test/java/com/pcitech/http/ingestion/HttpIngestionE2ETest.java`

- [ ] **Step 1: `ConnectorConfigFactory.cursorItemsConfig`**

```java
public static JsonNode cursorItemsConfig(ObjectMapper mapper, String baseUrl) {
    ObjectNode root = mapper.createObjectNode();
    // http.url = baseUrl + "/items"
    ObjectNode pagination = root.putObject("pagination");
    pagination.put("strategy", "cursor");
    pagination.put("location", "query");
    pagination.put("cursor_param", "cursor");
    pagination.put("page_size_param", "limit");
    pagination.put("page_size", 2);
    pagination.put("cursor_response_path", "$.meta.next");
    pagination.put("has_more_path", "$.meta.hasMore");
    pagination.putArray("stop_when").add("empty_cursor").add("has_more_false").add("empty_page");
    // transform input_root $.data, sink table users, keys [id]
    return root;
}
```

- [ ] **Step 2: WireMock stubs — `CursorPull` nested**

```java
@Nested
class CursorPull {
    @BeforeEach
    void stubCursorItems() {
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(get(urlPathEqualTo("/items"))
                .withQueryParam("limit", equalTo("2"))
                .withoutQueryParam("cursor")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}],\"meta\":{\"next\":\"c2\",\"hasMore\":true}}")));
        WIREMOCK.stubFor(get(urlPathEqualTo("/items"))
                .withQueryParam("cursor", equalTo("c2"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"id\":3,\"name\":\"C\"}],\"meta\":{\"next\":\"\",\"hasMore\":false}}")));
    }

    @Test
    void wireMockCursor_twoPagesThreeRows() throws Exception {
        JsonNode config = ConnectorConfigFactory.cursorItemsConfig(
                objectMapper, "http://localhost:" + WIREMOCK.getPort() + "/items");
        connectorService.create(new ConnectorRequestDto("e2e-cursor", "Cursor Items", "pull", config));
        connectorService.publish("e2e-cursor");
        JobRun job = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync("e2e-cursor", SyncService.SyncOptions.full()));
        assertThat(job.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(job.recordsOk()).isEqualTo(3);
        try (var connection = pgConnection()) {
            assertThat(PgTestSupport.countRows(connection, "users")).isEqualTo(3);
        }
    }
}
```

- [ ] **Step 3: 运行 E2E（需 Podman）**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am test "-Dtest=HttpIngestionE2ETest\$CursorPull" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS

---

## Task 7: Admin UI 向导

**Files:**
- Modify: `http-ingestion-admin-ui/src/api/client.ts`
- Modify: `http-ingestion-admin-ui/src/pages/ConnectorWizardPage.tsx`
- Modify: `http-ingestion-admin-ui/src/i18n/types.ts`
- Modify: `http-ingestion-admin-ui/src/i18n/locales/zh-CN.ts`
- Modify: `http-ingestion-admin-ui/src/i18n/locales/en-US.ts`

- [ ] **Step 1: `defaultConfig.pagination` 增加 cursor 默认值**

```typescript
pagination: {
  strategy: 'page_page_size',
  location: 'query',
  page_param: 'page',
  page_size_param: 'page_size',
  page_start: 1,
  page_size: 100,
  max_pages: 1000,
  cursor_param: 'cursor',
  cursor_response_path: '$.meta.nextCursor',
  has_more_path: '$.meta.hasMore',
  first_page_omit_cursor: true,
  stop_when: ['empty_cursor', 'empty_page'],
  total_count: { ... },
},
```

- [ ] **Step 2: 向导分页步骤 — 策略 Select + 条件渲染**

在 step 3 区域顶部添加：

```tsx
const paginationStrategy = Form.useWatch(['pagination', 'strategy'], form) ?? 'page_page_size';
const isCursor = paginationStrategy === 'cursor';

<Form.Item name={['pagination', 'strategy']} label={t('connectorWizard.paginationStrategy')}>
  <Select options={[
    { value: 'page_page_size', label: 'page / page_size' },
    { value: 'offset_limit', label: 'offset / limit' },
    { value: 'cursor', label: 'cursor' },
  ]} />
</Form.Item>

{isCursor ? (
  <>
    <Form.Item name={['pagination', 'location']} label={t('connectorWizard.paginationLocation')}>
      <Select options={[{ value: 'query', label: 'query' }, { value: 'body', label: 'body' }]} />
    </Form.Item>
    <Form.Item name={['pagination', 'cursor_param']} label={t('connectorWizard.cursorParam')} rules={[{ required: true }]}>
      <Input data-testid="cursor-param" />
    </Form.Item>
    <Form.Item name={['pagination', 'cursor_response_path']} label={t('connectorWizard.cursorResponsePath')} rules={[{ required: true }]}>
      <Input placeholder="$.meta.nextCursor" data-testid="cursor-response-path" />
    </Form.Item>
    <Form.Item name={['pagination', 'has_more_path']} label={t('connectorWizard.hasMorePath')}>
      <Input placeholder="$.meta.hasMore" />
    </Form.Item>
    <Form.Item name={['pagination', 'first_page_omit_cursor']} label={t('connectorWizard.firstPageOmitCursor')} valuePropName="checked">
      <Switch />
    </Form.Item>
    <Form.Item name={['pagination', 'stop_when']} label={t('connectorWizard.stopWhen')}>
      <Checkbox.Group options={[
        { label: 'empty_cursor', value: 'empty_cursor' },
        { label: 'has_more_false', value: 'has_more_false' },
        { label: 'empty_page', value: 'empty_page' },
      ]} />
    </Form.Item>
    <Form.Item name={['pagination', 'page_size_param']} label={t('connectorWizard.pageSizeParam')}>
      <Input />
    </Form.Item>
    <Form.Item name={['pagination', 'page_size']} label={t('connectorWizard.pageSize')}>
      <InputNumber min={1} max={1000} style={{ width: 200 }} />
    </Form.Item>
  </>
) : (
  /* 现有 page_param / total_count 字段 */
)}
```

- [ ] **Step 3: i18n 文案（zh-CN / en-US / types）**

新增 keys：`paginationStrategy`, `paginationLocation`, `cursorParam`, `cursorResponsePath`, `hasMorePath`, `firstPageOmitCursor`, `stopWhen`

- [ ] **Step 4: 本地 UI 构建**

```powershell
cd http-ingestion-admin-ui; npm run build
```

---

## Task 8: Playwright UI E2E

**Files:**
- Create: `http-ingestion-admin-ui/e2e/cursor-pagination.spec.ts`
- Modify: `http-ingestion-admin-ui/e2e/connector-list.spec.ts`

- [ ] **Step 1: 更新模板列表断言**

```typescript
const EXAMPLE_TEMPLATE_IDS = [
  'rest-pagination',
  'rest-offset-limit',
  'rest-kafka',
  'rest-cursor',
  'webhook-json-array',
] as const;
```

- [ ] **Step 2: 新建 `cursor-pagination.spec.ts`**

```typescript
import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Cursor pagination', () => {
  test('rest-cursor template full sync via mock', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-cursor');
    await createConnectorFromTemplate(
      page,
      'rest-cursor',
      connectorId,
      `Cursor ${connectorId}`,
      `${baseURL}/mock/e2e/cursor-items`,
      { enableSchedule: false },
    );
    await page.getByTestId('publish-btn').click();
    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, 'full', 'success');
    await expect(page.getByText(/成功|success/i)).toBeVisible();
  });
});
```

- [ ] **Step 3: 运行 Playwright（需预构建 jar + Podman）**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am package -DskipTests
cd http-ingestion-admin-ui
$env:E2E_SERVER_PORT="18080"
npm run test:e2e -- e2e/cursor-pagination.spec.ts e2e/connector-list.spec.ts
```

Expected: PASS

---

## Task 9: 文档与 CI 门禁

**Files:**
- Modify: `docs/testing/test-matrix.md`
- Modify: `README.md`

- [ ] **Step 1: test-matrix 追加**

| 测试 | 场景 |
|------|------|
| `CursorPaginationSupportTest` | stop_when OR 语义 |
| `HttpIngestionE2ETest.CursorPull` | WireMock query cursor 2 页 |
| `cursor-pagination.spec.ts` | rest-cursor → Mock 全量 sync |

- [ ] **Step 2: README 示例模板改为 5 个，列出 `rest-cursor`**

- [ ] **Step 3: 全量 CI 门禁**

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am test `
  -Dtest=HttpIngestionE2ETest,OpenApiImportE2ETest,JiaduSignVerifierTest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql*,KafkaSinkE2ETest,OpenApiImportServiceTest,CursorPaginationSupportTest `
  -Dsurefire.failIfNoSpecifiedTests=false
cd http-ingestion-admin-ui; npm run test:e2e
```

---

## Spec Coverage Self-Review

| Spec 要求 | 对应 Task |
|-----------|-----------|
| strategy cursor Query+Body | Task 3, 4 |
| stop_when 三种 OR | Task 3 |
| timestamp 增量并存 | Task 3（保留 applyIncremental） |
| rest-cursor 模板 | Task 5 |
| Mock endpoint | Task 5 |
| 向导 UI | Task 7 |
| Java E2E WireMock | Task 6 |
| Playwright | Task 8 |
| test-matrix | Task 9 |
| OpenAPI 推断（范围外） | — |

---

## 预估

| 任务 | 时间 |
|------|------|
| Task 1–4 core | 2–3 天 |
| Task 5–6 模板+E2E | 1 天 |
| Task 7–8 UI | 1–2 天 |
| Task 9 文档+回归 | 0.5 天 |

**合计：约 5–7 人天**
