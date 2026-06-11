# HTTP Ingestion Service

HTTP 数据接入服务：通过可配置连接器从外部 HTTP API **Pull** 同步数据到 PostgreSQL / Kafka，并提供 Admin UI 管理连接器、调度与任务排错。

## 能力

- **OpenAPI 导入**：粘贴/上传/URL 拉取 OAS3 与 Swagger 2.0（含 `/v2/api-docs`），多选批量创建或进入向导配置
- **Pull 连接器**：分页（`page_page_size` / **offset_limit**）、增量水位、Transform 映射、试请求与 Schema 推断
- **HTTP 配置**：Postman 式 Params / Headers / Body（JSON / form-urlencoded），树形 JSON Body 编辑
- **Sink**：**PostgreSQL Upsert** | **Kafka JSON**（可选）
- **Push 接入**：Webhook `POST /ingress/{connectorId}`（佳都 EventInfo 等，Wave 3）
- **示例模板**（5 个）：`rest-pagination`、`rest-offset-limit`、`rest-cursor`、`rest-kafka`、`webhook-json-array`
- **Mock 集成源**（`e2e` profile）：`/mock/dahua/*`、`/mock/meiya/*`、`/mock/jiadu/push/*`（回归测试用，非内置连接器模板）
- **Quartz 调度**：Cron / `fixed_rate`、暂停/恢复
- **Admin UI**：连接器 CRUD、发布、同步、排错、配置导入/导出
- **可观测**：`/actuator/health`（含 `externalKafka`）、`/actuator/prometheus`

路线图见 `docs/superpowers/specs/2026-06-06-mvp-pilot-phase15-quality-roadmap-design.md`（部分试点条目已随 OpenAPI 通用化方向调整）。

## 快速开始：OpenAPI 导入

1. 打开 Admin UI → **从 OpenAPI 导入**
2. 粘贴 OpenAPI JSON/YAML，或填写文档 URL（如 `http://host/v2/api-docs`）→ **解析文档**
3. 搜索并勾选接口 → **进入向导配置**（单选）或 **批量创建草稿**（多选，ID 为 `openapi-{operationId}` 派生）

导入后会自动填充 HTTP URL/方法、Params/Headers/Body 示例值，以及请求/响应 Schema（`openapi_meta`）。

## 前置

| 工具 | 版本 |
|------|------|
| JDK | 21 |
| Maven | wrapper（`mvnw`） |
| Node.js | 20（UI / Playwright） |
| Podman 或 Docker | E2E 与一键部署 |

Windows JDK 路径示例：`.\mvnw-jdk21.ps1`（见 `mvnw-jdk21.ps1`）。

## 构建

```powershell
.\mvnw-jdk21.ps1 package
```

产物：`http-ingestion-boot/target/http-ingestion-service.jar`（含 Admin UI 静态资源）。

## 本地运行

**单端口开发（推荐）**：`mvn package` 会自动编译 Admin UI 并复制到 `classpath:/static`，启动 JAR 后 API 与界面共用同一端口，无需单独运行 Node。

```powershell
$env:EXTERNAL_PG_URL="jdbc:postgresql://localhost:5432/postgres"
$env:EXTERNAL_PG_USER="postgres"
$env:EXTERNAL_PG_PASSWORD="postgres"
# 可选 Kafka Sink
# $env:EXTERNAL_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
java -jar http-ingestion-boot/target/http-ingestion-service.jar
```

浏览器打开 `http://localhost:8080/` 即可使用 Admin UI（与 API 同端口）。

如需 UI 热更新开发，可在 `http-ingestion-admin-ui` 目录运行 `npm run dev`（需配置 API 代理）；日常联调优先使用单 JAR 方式。

## Podman 一键部署

```powershell
.\scripts\podman\deploy.ps1
```

详见 `scripts/podman/README.md`。

## 测试

### 后端（CI 同款）

```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "test" `
  "-Dtest=HttpIngestionE2ETest,OpenApiImportE2ETest,JiaduSignVerifierTest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql*,KafkaSinkE2ETest,OpenApiImportServiceTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

需要 Podman/Docker（Testcontainers）。

> **政策**：此后每个新功能必须同时交付 Java E2E 与 Playwright UI 测试，见 `AGENTS.md`。

### Playwright UI E2E

```powershell
.\mvnw-jdk21.ps1 "-pl" "http-ingestion-boot" "-am" "package" "-DskipTests"
cd http-ingestion-admin-ui
$env:E2E_SERVER_PORT="18080"
npm run test:e2e
```

### 测试矩阵

见 `docs/testing/test-matrix.md`。

## Mock API

| 路径 | 说明 |
|------|------|
| `POST /mock/dahua/gretrieval/vehicle/query` | 大华车辆分页（E2E Mock） |
| `POST /mock/dahua/gretrieval/vehicle/count` | 大华统计 |
| `POST /mock/meiya/api/res/trafficPoliceAlert` | 美亚警情 |
| `POST /mock/meiya/api/res/dispatch110Flow` | 美亚 110 流水 |

**测试专用**（`spring.profiles.active=e2e`）：

| 路径 | 说明 |
|------|------|
| `POST /mock/_test/reset` | 重置 Mock 数据为默认集 |
| `POST /mock/_test/dahua/vehicles` | 追加大华记录 |
| `POST /mock/_test/meiya/traffic-police` | 追加美亚警情 |
| `POST /mock/_test/meiya/dispatch110` | 追加美亚 110 流水 |

## 文档

- 设计：`docs/superpowers/specs/2026-06-05-http-ingestion-service-design.md`
- 路线图：`docs/superpowers/specs/2026-06-06-mvp-pilot-phase15-quality-roadmap-design.md`
- 佳都 Push（Wave 3）：`docs/api/jiadu-push-integration.md`
- 运维：`docs/ops/monitoring.md`、`docs/ops/backup-restore.md`

## CI

PR 触发 `.github/workflows/ci.yml`：Maven test → Playwright E2E。
