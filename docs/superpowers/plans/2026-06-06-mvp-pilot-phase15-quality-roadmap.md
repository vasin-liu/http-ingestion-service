# MVP 试点 + Phase 1.5 + 工程质量 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已评审路线图（Wave 1→2→3）交付 CI/Podman 一键部署、四套 Pull 多轮自动化、7 天试点、佳都 Push Webhook（EventInfo/ResultInfo）、调度与分页扩展。

**Architecture:** 现有 Spring Boot 单体 + H2 元库 + 外部 PG Sink 不变；Wave 1 补工程交付物（CI、Podman compose 挂载 jar、Mock 测试 API、多轮 E2E）；Wave 2 试点观测与调度 UI；Wave 3 新增 `/ingress/{connectorId}` Push 管道与 `jiadu_event_info` 表。sign 校验默认关闭。

**Tech Stack:** Java 21, Spring Boot 3.4, PostgreSQL 16, Quartz, React Admin UI, Playwright, Podman Compose, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-06-06-mvp-pilot-phase15-quality-roadmap-design.md`

---

## File Structure（新增/主要修改）

| 路径 | 职责 |
|------|------|
| `.github/workflows/ci.yml` | PR 门禁：Maven test + Playwright |
| `deploy/podman-compose.yml` | postgres + JRE 容器挂载 jar |
| `deploy/init-pg.sql` | 试点 PG DDL（含 `jiadu_event_info` 占位） |
| `deploy/.env.example` | compose 环境变量模板 |
| `scripts/podman/deploy.ps1` / `deploy.sh` | 一键 build + up + health 轮询 |
| `scripts/podman/teardown.ps1` / `teardown.sh` | compose down |
| `scripts/podman/README.md` | Podman/WSL2 说明 |
| `http-ingestion-api/.../mock/MockIntegrationTestController.java` | 测试专用 reset/append API |
| `http-ingestion-boot/.../HttpIngestionE2ETest.java` | 新增 `PullMultiRound` nested |
| `http-ingestion-boot/.../support/PullMultiRoundSupport.java` | R1–R4 共用断言 |
| `README.md` | 项目入口文档 |
| `docs/testing/test-matrix.md` | 测试层级矩阵 |
| `docs/api/jiadu-push-integration.md` | 佳都 Push 契约（Wave 1 骨架，Wave 3 补全） |
| `docs/ops/prometheus-scrape.example.yml` | 试点 scrape 示例 |
| `docs/ops/pilot-report-template.md` | 7 天报告模板 |
| Wave 3: `http-ingestion-api/.../ingress/` | Webhook Ingress + ResultInfo |
| Wave 3: `http-ingestion-api/.../mock/MockJiaduPushSimulator.java` | Push 多轮模拟 |

---

# Wave 1（第 1–2 周）— 工程质量 + 试点启动

**Wave 1 出口：** Podman 一键部署 OK；CI 绿；大华 query + 美亚 traffic-police 各完成 R1–R4 Java E2E。

---

## Task 1: GitHub Actions CI 流水线

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: 创建 workflow 文件**

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main, master]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Backend tests
        run: |
          ./mvnw -pl http-ingestion-boot -am test \
            -Dtest=HttpIngestionE2ETest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql* \
            -Dsurefire.failIfNoSpecifiedTests=false

  ui-e2e:
    needs: backend
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: http-ingestion-admin-ui/package-lock.json
      - name: Install Podman
        run: |
          sudo apt-get update
          sudo apt-get install -y podman
      - name: Build jar
        run: ./mvnw -pl http-ingestion-boot -am package -DskipTests
      - name: Playwright E2E
        working-directory: http-ingestion-admin-ui
        env:
          E2E_SERVER_PORT: '18080'
          CI: 'true'
        run: |
          npm ci
          npx playwright install chromium --with-deps
          npm run test:e2e
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: playwright-report
          path: http-ingestion-admin-ui/playwright-report/
```

- [ ] **Step 2: 本地模拟 CI backend job**

Run:
```powershell
cd D:\Work\99_Code\http-ingestion-service
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "test" "-Dtest=HttpIngestionE2ETest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql*" "-Dsurefire.failIfNoSpecifiedTests=false"
```
Expected: `BUILD SUCCESS`，11+ tests pass

- [ ] **Step 3: 本地模拟 CI ui-e2e job**

Run:
```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "package" "-DskipTests"
cd http-ingestion-admin-ui
$env:E2E_SERVER_PORT="18080"
npm run test:e2e
```
Expected: 5 Playwright tests pass

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add Maven and Playwright PR gate"
```

---

## Task 2: Mock 测试专用 append/reset API

**Files:**
- Create: `http-ingestion-api/src/main/java/com/pcitech/http/ingestion/api/mock/MockIntegrationTestController.java`
- Modify: `http-ingestion-boot/src/test/java/com/pcitech/http/ingestion/HttpIngestionE2ETest.java`（新增 API 冒烟）

**背景:** `MockIntegrationSourceStore` 已有 `reset()` / `addDahuaVehicle()` 等方法，但 E2E/Playwright 无法通过 HTTP 多轮追加。新增仅 `e2e` profile 启用的控制器。

- [ ] **Step 1: 写失败测试**

在 `HttpIngestionE2ETest` 新增 nested class：

```java
@Nested
class MockTestApi {

    @Test
    void resetAndAppendDahuaViaHttp() {
        var reset = restTemplate.postForEntity("/mock/_test/reset", null, Map.class);
        assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();

        var append = restTemplate.postForEntity(
                "/mock/_test/dahua/vehicles",
                Map.of(
                        "recordId", "rec-http-001",
                        "plateNum", "粤D99999",
                        "capTime", "20250601T110000Z",
                        "channelName", "卡口HTTP",
                        "plateType", "02"
                ),
                Map.class
        );
        assertThat(append.getStatusCode().is2xxSuccessful()).isTrue();

        var query = restTemplate.postForEntity(
                "/mock/dahua/gretrieval/vehicle/query",
                Map.of("page", 1, "pageSize", 100, "condition", Map.of()),
                Map.class
        );
        assertThat(query.getBody()).containsEntry("totalCount", 1);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "test" "-Dtest=HttpIngestionE2ETest\$MockTestApi" "-Dsurefire.failIfNoSpecifiedTests=false"
```
Expected: FAIL — 404 on `/mock/_test/reset`

- [ ] **Step 3: 实现 MockIntegrationTestController**

```java
package com.pcitech.http.ingestion.api.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Profile("e2e")
@RequestMapping("/mock/_test")
public class MockIntegrationTestController {

    private final MockIntegrationSourceStore store;

    public MockIntegrationTestController(MockIntegrationSourceStore store) {
        this.store = store;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.reset();
        return Map.of("ok", true);
    }

    @PostMapping("/dahua/vehicles")
    public Map<String, Object> appendDahua(@RequestBody Map<String, Object> record) {
        store.addDahuaVehicle(record);
        return Map.of("ok", true, "total", storeSnapshotSize("dahua"));
    }

    @PostMapping("/meiya/traffic-police")
    public Map<String, Object> appendMeiyaTrafficPolice(@RequestBody Map<String, Object> record) {
        store.addMeiyaTrafficPolice(record);
        return Map.of("ok", true);
    }

    @PostMapping("/meiya/dispatch110")
    public Map<String, Object> appendMeiyaDispatch110(@RequestBody Map<String, Object> record) {
        store.addMeiyaDispatch110(record);
        return Map.of("ok", true);
    }

    private int storeSnapshotSize(String kind) {
        // 通过 query 端点间接计数，或给 Store 加 count 方法
        return switch (kind) {
            case "dahua" -> {
                var r = store.queryDahuaVehicles(1, 1000, null, null);
                yield (Integer) r.get("totalCount");
            }
            default -> -1;
        };
    }
}
```

同时在 `MockIntegrationSourceStore` 增加 `int dahuaVehicleCount()` 等只读计数方法（避免 controller 重复 query 逻辑）：

```java
public int dahuaVehicleCount() {
    return dahuaVehicleRecords.size();
}
```

- [ ] **Step 4: 确保 E2E profile 激活**

确认 `AbstractIntegrationE2ETest` 或 `application-e2e.yml` 在测试中激活 `e2e` profile。在测试类上加：

```java
@ActiveProfiles("e2e")
```

或在 `src/test/resources/application.properties` 添加：

```properties
spring.profiles.active=e2e
```

- [ ] **Step 5: 运行测试通过**

Run 同 Step 2。Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add http-ingestion-api/src/main/java/com/pcitech/http/ingestion/api/mock/
git add http-ingestion-boot/src/test/
git commit -m "feat(mock): add e2e-only reset/append test API"
```

---

## Task 3: Pull 多轮 E2E（R1–R4 参数化）

**Files:**
- Create: `http-ingestion-boot/src/test/java/com/pcitech/http/ingestion/support/PullMultiRoundSupport.java`
- Modify: `http-ingestion-boot/src/test/java/com/pcitech/http/ingestion/HttpIngestionE2ETest.java`

- [ ] **Step 1: 创建 PullMultiRoundSupport**

```java
package com.pcitech.http.ingestion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import com.pcitech.http.ingestion.core.service.ConnectorService;
import com.pcitech.http.ingestion.core.service.SyncService;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.sql.Connection;
import java.util.Map;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

public final class PullMultiRoundSupport {

    private PullMultiRoundSupport() {}

    public record Scenario(
            String connectorId,
            String name,
            JsonNode config,
            String table,
            int initialRows,
            Runnable appendRound2,
            Runnable appendRound3,
            int afterRound2Rows,
            int afterRound3Rows
    ) {}

    public static void runR1ToR4(
            ConnectorService connectorService,
            SyncService syncService,
            JobRunRepository jobRunRepository,
            TestRestTemplate restTemplate,
            IntSupplier pgRowCount,
            ConnectionSupplier connectionSupplier,
            Scenario scenario
    ) throws Exception {
        // R1: full sync
        connectorService.create(new ConnectorRequestDto(
                scenario.connectorId(), scenario.name(), "pull", scenario.config()));
        connectorService.publish(scenario.connectorId());
        JobRun r1 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.full()));
        assertThat(r1.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.initialRows());

        // R2: append + incremental
        scenario.appendRound2().run();
        JobRun r2 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.incremental()));
        assertThat(r2.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.afterRound2Rows());

        // R3: append again + incremental
        scenario.appendRound3().run();
        JobRun r3 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.incremental()));
        assertThat(r3.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.afterRound3Rows());

        // R4: reset state + full rebuild
        var reset = restTemplate.postForEntity(
                "/api/connectors/" + scenario.connectorId() + "/state/reset", null, Void.class);
        assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();
        JobRun r4 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.full()));
        assertThat(r4.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.afterRound3Rows());
    }

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws Exception;
    }
}
```

- [ ] **Step 2: 在 HttpIngestionE2ETest 添加 PullMultiRound nested**

Wave 1 至少覆盖 2 套（spec 出口）；建议 4 套全做：

```java
@Nested
class PullMultiRound {

    @Test
    void dahuaVehicleQuery_r1ThroughR4() throws Exception {
        PullMultiRoundSupport.runR1ToR4(
                connectorService, syncService, jobRunRepository, restTemplate,
                () -> rowCount("dahua_vehicle_pass"),
                () -> pgConnection(),
                new PullMultiRoundSupport.Scenario(
                        "mr-dahua-query", "MR Dahua Query",
                        ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()),
                        "dahua_vehicle_pass", 2,
                        () -> mockStore.addDahuaVehicle(dahuaRec("rec-r2", "20250601T100000Z")),
                        () -> mockStore.addDahuaVehicle(dahuaRec("rec-r3", "20250601T110000Z")),
                        3, 4
                )
        );
    }

    @Test
    void meiyaTrafficPolice_r1ThroughR4() throws Exception {
        // 类似，使用 addMeiyaTrafficPolice
    }

    private static Map<String, Object> dahuaRec(String id, String capTime) {
        return Map.of("recordId", id, "plateNum", "粤X" + id, "capTime", capTime,
                "channelName", "卡口", "plateType", "02");
    }

    private int rowCount(String table) throws Exception {
        try (var c = pgConnection()) {
            return PgTestSupport.countRows(c, table);
        }
    }
}
```

- [ ] **Step 3: 运行多轮测试**

Run:
```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "test" "-Dtest=HttpIngestionE2ETest\$PullMultiRound" "-Dsurefire.failIfNoSpecifiedTests=false"
```
Expected: PASS（2+ tests）

- [ ] **Step 4: 运行完整 E2E 套件**

Run:
```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "test" "-Dtest=HttpIngestionE2ETest" "-Dsurefire.failIfNoSpecifiedTests=false"
```
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add http-ingestion-boot/src/test/
git commit -m "test(e2e): add Pull multi-round R1-R4 scenarios"
```

---

## Task 4: Podman 一键部署（挂载 jar）

**Files:**
- Create: `deploy/podman-compose.yml`
- Create: `deploy/init-pg.sql`
- Create: `deploy/.env.example`
- Create: `scripts/podman/deploy.ps1`
- Create: `scripts/podman/deploy.sh`
- Create: `scripts/podman/teardown.ps1`
- Create: `scripts/podman/teardown.sh`
- Create: `scripts/podman/README.md`

- [ ] **Step 1: 创建 deploy/init-pg.sql**

复制 `http-ingestion-admin-ui/e2e/init-pg.sql` 内容，并追加 Wave 3 占位表（可先建空表）：

```sql
-- jiadu_event_info (Wave 3 Push; created early for pilot DDL parity)
CREATE TABLE IF NOT EXISTS jiadu_event_info (
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
);
```

- [ ] **Step 2: 创建 deploy/podman-compose.yml**

```yaml
services:
  postgres:
    image: docker.io/library/postgres:16-alpine
    environment:
      POSTGRES_PASSWORD: postgres
    ports:
      - "${PG_PORT:-5432}:5432"
    volumes:
      - ./init-pg.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 10

  http-ingestion:
    image: docker.io/library/eclipse-temurin:21-jre-alpine
    depends_on:
      postgres:
        condition: service_healthy
    ports:
      - "${APP_PORT:-8080}:8080"
    volumes:
      - ../http-ingestion-boot/target/http-ingestion-service.jar:/app/http-ingestion-service.jar:ro
      - ../data:/data
    command: ["java", "-jar", "/app/http-ingestion-service.jar"]
    environment:
      SERVER_PORT: "8080"
      SPRING_PROFILES_ACTIVE: "e2e"
      META_DB_PATH: /data
      EXTERNAL_PG_URL: jdbc:postgresql://postgres:5432/postgres
      EXTERNAL_PG_USER: postgres
      EXTERNAL_PG_PASSWORD: postgres
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
```

> 若 `META_DB_PATH` 与现有配置键不一致，对照 `application.yml` 中 `ingestion.meta-db` 实际键名调整 env。

- [ ] **Step 3: 创建 deploy.ps1**

```powershell
param([switch]$SkipBuild)
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

if (-not (Get-Command podman -ErrorAction SilentlyContinue)) {
    throw "podman not found on PATH"
}
if (-not $SkipBuild) {
    & "$Root\mvnw-jdk21.ps1" "-pl" "http-ingestion-boot" "-am" "package" "-DskipTests"
}
$Jar = Join-Path $Root "http-ingestion-boot\target\http-ingestion-service.jar"
if (-not (Test-Path $Jar)) { throw "Missing jar: $Jar" }

New-Item -ItemType Directory -Force -Path (Join-Path $Root "data") | Out-Null
Set-Location (Join-Path $Root "deploy")
podman compose -f podman-compose.yml up -d

$deadline = (Get-Date).AddMinutes(3)
do {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
        if ($r.StatusCode -eq 200) { break }
    } catch {}
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

Write-Host "UI/API: http://localhost:8080"
Write-Host "Mock:   http://localhost:8080/mock/..."
Write-Host "PG:     localhost:5432 postgres/postgres"
```

- [ ] **Step 4: 创建 deploy.sh**（Linux/macOS 等价逻辑，使用 `./mvnw`）

- [ ] **Step 5: 本地验证**

Run:
```powershell
.\scripts\podman\deploy.ps1
curl http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`

Run teardown:
```powershell
.\scripts\podman\teardown.ps1
```

- [ ] **Step 6: Commit**

```bash
git add deploy/ scripts/podman/
git commit -m "feat(deploy): add Podman compose with mounted jar"
```

---

## Task 5: 文档交付（Wave 1）

**Files:**
- Create: `README.md`
- Create: `docs/testing/test-matrix.md`
- Create: `docs/api/jiadu-push-integration.md`（骨架）
- Create: `docs/ops/prometheus-scrape.example.yml`
- Create: `docs/ops/pilot-report-template.md`

- [ ] **Step 1: 编写 README.md**

必含章节：
1. 项目简介（Pull 连接器 + Admin UI）
2. 前置：JDK 21、Podman、Node 20
3. 构建：`.\mvnw-jdk21.ps1 package`
4. 单元/E2E：`mvn test` 命令块（与 CI 一致）
5. Playwright：`cd http-ingestion-admin-ui && npm run test:e2e`
6. Podman 一键：`.\scripts\podman\deploy.ps1`
7. Mock 路径表（4 个集成源 + `/_test` API）
8. 链接：`docs/testing/test-matrix.md`、`docs/superpowers/specs/2026-06-06-...`

- [ ] **Step 2: 编写 test-matrix.md**

表格列：层级 | 命令 | 覆盖 | 多轮场景 | 前置

| 层级 | 命令 | 覆盖 |
|------|------|------|
| 单元 | `TransformPipelineTest` 等 | Transform/Composer |
| Java E2E | `HttpIngestionE2ETest` | 生命周期、Mock、4 套 Pull、调度、R1–R4 |
| Playwright | `npm run test:e2e` | 主题、列表、创建向导 |
| CI | `.github/workflows/ci.yml` | PR 全量 |

- [ ] **Step 3: jiadu-push-integration.md 骨架**

引用 §2.2.3 + §3；列出 EventInfo/ResultInfo 字段表（从 spec 复制）；注明 sign 默认关、regSubscribe 不实现。

- [ ] **Step 4: prometheus-scrape.example.yml**

```yaml
scrape_configs:
  - job_name: http-ingestion
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['localhost:8080']
```

- [ ] **Step 5: pilot-report-template.md**

7 日表格：日期 | 连接器 | 成功率 | records_ok | max_lag | incidents

- [ ] **Step 6: Commit**

```bash
git add README.md docs/
git commit -m "docs: add README, test matrix, ops templates"
```

---

## Task 6: Wave 1 整体验收

- [ ] **Step 1: 全量 backend test**

```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "test" "-Dtest=HttpIngestionE2ETest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql*" "-Dsurefire.failIfNoSpecifiedTests=false"
```

- [ ] **Step 2: Playwright**

```powershell
$env:E2E_SERVER_PORT="18080"
cd http-ingestion-admin-ui; npm run test:e2e
```

- [ ] **Step 3: Podman deploy 冒烟**

```powershell
.\scripts\podman\deploy.ps1
# 浏览器打开 http://localhost:8080
.\scripts\podman\teardown.ps1
```

- [ ] **Step 4: 更新 CHANGELOG.md**

```markdown
## [Unreleased] - Wave 1
- CI: GitHub Actions Maven + Playwright
- Deploy: Podman compose (JRE + jar mount)
- Mock: /mock/_test reset/append API
- E2E: Pull multi-round R1-R4
```

---

# Wave 2（第 3–4 周）— 7 天试点 + 调度补全

**Wave 2 出口：** 试点报告；fixed_rate + 调度暂停 UI；Playwright 覆盖四套 Pull R1–R2。

---

## Task 7: fixed_rate 调度后端

**Files:**
- Modify: `http-ingestion-scheduler/.../ConnectorScheduleService.java`（或等价类）
- Modify: `http-ingestion-core/...` schedule 配置解析
- Test: `HttpIngestionE2ETest.SchedulerSync` 新增 fixed_rate 用例

- [ ] **Step 1: 写失败测试 — fixed_rate 每 N 秒触发 incremental**

```java
@Test
void fixedRateTriggerRunsIncrementalSync() throws Exception {
    ObjectNode config = (ObjectNode) ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()).deepCopy();
    ObjectNode schedule = config.putObject("schedule");
    schedule.put("enabled", true);
    schedule.put("type", "fixed_rate");
    schedule.put("interval_seconds", 2);
    // publish → full → append → await row count increase within 30s
}
```

- [ ] **Step 2: 实现 SimpleScheduleBuilder 分支**

解析 `schedule.type=fixed_rate` + `interval_seconds` → Quartz `SimpleScheduleBuilder.repeatSecondlyForever(n)`。

- [ ] **Step 3: 测试通过 + Commit**

---

## Task 8: 调度暂停/恢复 UI

**Files:**
- Modify: `http-ingestion-admin-ui/src/pages/ConnectorDetailPage.tsx`（或等价）
- Modify: `http-ingestion-api/...` 新增 `POST /api/connectors/{id}/schedule/pause|resume`
- Test: `http-ingestion-admin-ui/e2e/connector-schedule.spec.ts`

- [ ] **Step 1: 后端 pause/resume API**

调用 Quartz `scheduler.pauseJob(jobKey)` / `resumeJob(jobKey)`，同步更新 `connector_schedule.enabled`。

- [ ] **Step 2: UI 按钮 + data-testid**

`data-testid="schedule-pause"` / `schedule-resume`

- [ ] **Step 3: Playwright  spec**

创建带 schedule 的连接器 → pause → 追加 Mock 数据 → 等待 10s 行数不变 → resume → 行数增加

- [ ] **Step 4: Commit**

---

## Task 9: 7 天试点执行（运维）

**Files:**
- Create: `docs/ops/pilot-report-2026-06.md`（运行期填写）

- [ ] **Step 1:** Podman 部署准生产 PG，publish 四套连接器，Cron `0 0/15 * * * ?`
- [ ] **Step 2:** 配置 Prometheus scrape（`prometheus-scrape.example.yml`）
- [ ] **Step 3:** 每日记录 `ingestion_job_total`、`ingestion_watermark_lag_seconds`
- [ ] **Step 4:** 对照 `docs/superpowers/specs/2026-06-05-http-ingestion-mvp-stories.md` 勾验
- [ ] **Step 5:** 备份恢复演练（`docs/ops/backup-restore.md`）
- [ ] **Step 6:** 输出 Go/No-Go 报告

---

## Task 10: Playwright 四套 Pull 扩展

**Files:**
- Create: `http-ingestion-admin-ui/e2e/pull-multi-round.spec.ts`
- Modify: `e2e/connector-flow.spec.ts`

- [ ] **Step 1:** 参数化 4 模板 wizard 创建（Mock baseUrl）
- [ ] **Step 2:** R1 全量 sync → 断言 job success
- [ ] **Step 3:** 调用 `/mock/_test/...` append → R2 增量 → 详情页 job 列表有 incremental
- [ ] **Step 4:** 至少 dahua-query + meiya-traffic-police 两套在 CI 跑通

---

# Wave 3（第 5–7 周）— Webhook Push + offset/limit

**Wave 3 出口：** Jiadu Push R1–P4 E2E 全绿；offset/limit E2E；`jiadu-push-integration.md` 完整。

---

## Task 11: jiadu_event_info DDL 与模板

**Files:**
- Modify: `deploy/init-pg.sql`, `PgTestSupport.java`, `e2e/init-pg.sql`
- Create: `http-ingestion-core/.../templates/jiadu-event-push.json`（或 Java 内置模板注册）

- [ ] **Step 1:** 统一 DDL（与 spec §5.1.1 字段对齐）
- [ ] **Step 2:** 注册 `jiadu-event-push` 模板（mode=push，webhook.verify_sign=false 默认）

---

## Task 12: Webhook Ingress 核心

**Files:**
- Create: `http-ingestion-api/src/main/java/com/pcitech/http/ingestion/api/ingress/JiaduIngressController.java`
- Create: `http-ingestion-api/src/main/java/com/pcitech/http/ingestion/api/ingress/ResultInfo.java`
- Create: `http-ingestion-api/src/main/java/com/pcitech/http/ingestion/api/ingress/JiaduSignVerifier.java`
- Create: `http-ingestion-core/src/test/java/.../JiaduSignVerifierTest.java`

- [ ] **Step 1: 写 ResultInfo record**

```java
public record ResultInfo(int OpCode, String OpDesc) {}
```

- [ ] **Step 2: 写 SignVerifier 单元测试**

```java
@Test
void md5Uppercase_platFlagUnderscoreEventId() {
    assertThat(JiaduSignVerifier.compute("ivsp", "UUID00001"))
        .isEqualTo("..."); // 与文档样例对齐
}
```

- [ ] **Step 3: 实现 JiaduIngressController**

```java
@PostMapping("/ingress/{connectorId}")
public ResultInfo ingest(
        @PathVariable String connectorId,
        @RequestHeader(value = "x_request_id", required = false) String requestId,
        @RequestHeader(value = "sign", required = false) String sign,
        @RequestBody JsonNode body
) {
    // 1. load published connector; mode push|both
    // 2. if webhook.verify_sign → verify or return OpCode=1
    // 3. normalize ImageUrl → ImgUrl
    // 4. run push pipeline → PG upsert event_id
    // 5. job_run type=push
    // 6. return ResultInfo(0, "成功") or OpCode!=0
}
```

- [ ] **Step 4: E2E — HttpIngestionE2ETest.JiaduPushIngress**

| 测试 | 断言 |
|------|------|
| P1 10 events | PG=10, OpCode=0 |
| P2 2 dup + 3 new | PG=13 |
| P3 bad sign (verify_sign=true 子集) | OpCode≠0 |
| P4 ImgUrl vs ImageUrl | 均落库 img_url |

- [ ] **Step 5: Commit**

---

## Task 13: MockJiaduPushSimulator

**Files:**
- Create: `MockJiaduPushStore.java`, `MockJiaduPushSimulator.java`（`@Profile("e2e")`）
- Endpoint: `POST /mock/jiadu/push/{connectorId}?rounds=10`

- [ ] **Step 1:** 生成 EventInfo JSON（§3 必填字段）
- [ ] **Step 2:** 可选 sign 头（verify_sign 测试用）
- [ ] **Step 3:** Playwright：创建 push 连接器 → 调 simulator → 详情页见 push job

---

## Task 14: offset/limit 分页策略

**Files:**
- Modify: `http-ingestion-core/.../pagination/` 策略注册
- Create: `offset_limit` 策略实现
- Test: WireMock 多页 E2E + 模板 `rest-offset-limit`

- [ ] **Step 1: 写失败单元测试**

```java
@Test
void offsetLimit_pagesUntilEmpty() {
    // offset=0, limit=2 → 2 pages
}
```

- [ ] **Step 2: 实现策略**（`offset_param`, `limit_param`, `page_start`）
- [ ] **Step 3: E2E 3 页 WireMock stub → PG 6 行**
- [ ] **Step 4: Commit**

---

## Task 15: Wave 3 文档与 CI 扩展

- [ ] **Step 1:** 补全 `docs/api/jiadu-push-integration.md`（EventRegQo、sign 规则、OpCode 语义）
- [ ] **Step 2:** CI backend 增加 `JiaduPushIngress` 测试类名到 `-Dtest`
- [ ] **Step 3:** Playwright 新增 push 冒烟 spec
- [ ] **Step 4:** CHANGELOG Wave 3 条目

---

# Spec Self-Review（计划 vs 规格）

| 规格要求 | 计划 Task |
|----------|-----------|
| CI PR Playwright | Task 1 |
| Podman 挂载 jar | Task 4 |
| sign 默认关 | Task 12（Wave 3 实现，默认 false） |
| Mock 多轮 append | Task 2, 3 |
| 四套 Pull R1–R4 | Task 3（Wave 1 2 套+），Task 10（Playwright 4 套） |
| 7 天试点 | Task 9 |
| fixed_rate + 调度 UI | Task 7, 8 |
| Webhook EventInfo/ResultInfo | Task 11, 12, 13 |
| offset/limit | Task 14 |
| jiadu 文档 §2.2.3 + §3 | Task 5 骨架, Task 15 完整 |
| Kafka Wave 4 可选 | 未纳入（符合范围外） |
| regSubscribe 不实现 | 仅文档 Task 5/15 |

**Placeholder 扫描:** 无 TBD；env 键名 Task 4 注明需对照 `application.yml` 验证。

---

# 执行顺序建议

```
Wave 1: Task 1 → 2 → 3 → 4 → 5 → 6
Wave 2: Task 7 → 8 → 10（并行）→ Task 9（需环境）
Wave 3: Task 11 → 12 → 13 → 14 → 15
```

**预估工时:** Wave 1 ≈ 3–5 天；Wave 2 ≈ 2 周（含 7 天观测）；Wave 3 ≈ 2–3 周。
