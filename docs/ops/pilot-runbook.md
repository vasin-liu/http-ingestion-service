# 7 天试点运行手册

> 对应 Wave 2 Track A。报告模板：`docs/ops/pilot-report-template.md`  
> **2026-06-10 更新**：内置厂商 Pull 模板已移除；试点以 **OpenAPI 导入**（真实 API）或 **示例模板 + Mock**（观测演练）为主。

## 启动前

- [ ] Podman Machine 已启动（`podman machine start`；Testcontainers / Playwright / deploy 均依赖容器运行时）
- [ ] `.\scripts\podman\deploy.ps1` 成功，或客户提供的业务 PG 已就绪
- [ ] `deploy/init-pg.sql` 业务表已存在（`items`、`users`、`jiadu_event_info`、`webhook_events` 等）
- [ ] Prometheus scrape 已配置（见 `docs/ops/prometheus-scrape.example.yml`）
- [ ] 试点连接器已 **publish**（见下文两种路径）

### 一键部署

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am package -DskipTests
.\scripts\podman\deploy.ps1
```

浏览器访问 `http://localhost:8080/`。健康检查：`curl http://localhost:8080/actuator/health`

## 试点连接器配置

### 路径 A — 真实 API（推荐）

适用于对接客户或内部 HTTP 源。

**一键 bootstrap**（OpenAPI 批量导入 + 发布 + 首日全量）：

```powershell
Copy-Item .\scripts\pilot\pilot-openapi.config.example.json .\scripts\pilot\pilot-openapi.config.json
# 编辑 pilot-openapi.config.json：specPath/specUrl、operations、sink 表与 Cron
.\scripts\pilot\setup-openapi-pilot.ps1
.\scripts\pilot\collect-daily-metrics.ps1   # 每日执行
```

报告：`docs/ops/pilot-report-openapi-2026-06.md`。

**本地 dry-run**（无真实 API）：`.\scripts\pilot\setup-openapi-pilot.ps1 -ConfigPath .\scripts\pilot\pilot-openapi.config.mock-demo.json`

**手工步骤**（与脚本等价）：

1. Admin UI → **从 OpenAPI 导入**
2. 粘贴 OpenAPI JSON/YAML，或填写文档 URL（如 `http://host/v2/api-docs`）→ **解析文档**
3. 勾选目标接口 → **进入向导配置**（单接口）或 **批量创建草稿**（多接口）
4. 在向导中完成：HTTP URL、分页/增量、Transform 映射、Sink 表与主键
5. **发布** 并配置 Cron（建议错开分钟，见下表）

导入后连接器 ID 通常为 `openapi-{operationId}` 派生；配置含 `openapi_meta` 请求/响应 Schema。`setup-openapi-pilot.ps1` 会应用解析结果中的 `suggestedPagination`（若存在）。

### 路径 B — Mock 观测演练

适用于无真实源地址时验证调度、指标与 UI 排错。使用 **示例模板** + 内置 Mock（`e2e` profile 下可用；`deploy.ps1` 默认启用）。

**一键 bootstrap**（推荐）：

```powershell
.\scripts\podman\deploy.ps1
.\scripts\pilot\setup-mock-pilot.ps1
.\scripts\pilot\collect-daily-metrics.ps1   # 每日执行
```

报告：`docs/ops/pilot-report-2026-06.md`。详见 `scripts/pilot/README.md`。

| 模板 ID | 试点 connector_id | 目标表 | Cron 建议 | Mock URL（相对服务根） |
|---------|-------------------|--------|-----------|------------------------|
| `rest-pagination` | `pilot-mock-pagination` | `items` | `0 0/15 * * * ?` | `/mock/e2e/pagination-items` |
| `rest-offset-limit` | `pilot-mock-offset-limit` | `items` | `0 5/15 * * * ?` | 同上 |
| `rest-cursor` | `pilot-mock-cursor` | `items` | `0 10/15 * * * ?` | `/mock/e2e/cursor-items` |
| `rest-kafka` | `pilot-mock-kafka` | Kafka topic | `0 15/15 * * * ?` | `/mock/e2e/kafka-users` |
| `rest-monotonic-id` | `pilot-mock-monotonic-id` | `items` | `0 20/15 * * * ?` | `/mock/e2e/monotonic-items` |
| `rest-rolling-window` | `pilot-mock-rolling-window` | `items` | `0 25/15 * * * ?` | `/mock/e2e/window-items` |
| Jiadu Push | `pilot-mock-jiadu` | `jiadu_event_info` | 无 Cron | `POST /mock/jiadu/push/{connectorId}` |

**佳都 Push 演练**：`setup-mock-pilot.ps1` 默认创建 `pilot-mock-jiadu` 并发送 3 轮模拟事件；详见 `docs/api/jiadu-push-integration.md`。

Cron 错开分钟，降低 PG、HTTP 与调度争用。

### 路径 C — 遗留 Mock 回归（可选）

`/mock/dahua/*`、`/mock/meiya/*` 仍可用于 Java E2E 回归，但 **不再提供内置连接器模板**。若试点需覆盖历史表结构，请通过 OpenAPI 导入或空白向导手工配置 URL 与 Transform，并对照 `deploy/init-pg.sql` 中的 `dahua_*` / `meiya_*` 表。

## 每日观测（7 天）

1. Admin UI → 各连接器详情 → **运行历史** 成功率与最近错误
2. 拉取指标：
   ```powershell
   curl http://localhost:8080/actuator/prometheus | findstr ingestion_
   ```
3. 记录：`ingestion_job_total`、`ingestion_job_duration_seconds`、`ingestion_watermark_lag_seconds`（按 `connector_id`）
4. 连续 3 次 `failed` → 按 `docs/ops/monitoring.md` 处理

## NF 验收

对照 `docs/superpowers/specs/2026-06-05-http-ingestion-mvp-stories.md` 逐项勾选（Phase 2 明确范围外项可标注 N/A）。

## 备份恢复

按 `docs/ops/backup-restore.md` 演练一次元库（H2）+ 业务 PG 备份与恢复。

## 结束

填写 `docs/ops/pilot-report-YYYY-MM.md`（连接器列表改为实际 `connector_id` + Cron + Sink），给出 **Go / No-Go**。
