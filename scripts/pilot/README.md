# 7 天试点脚本

- **路径 B（Mock）**：`setup-mock-pilot.ps1` — 示例模板 + 内置 Mock  
- **路径 A（OpenAPI）**：`setup-openapi-pilot.ps1` — 真实 API / OpenAPI 批量导入  

对应 `docs/ops/pilot-runbook.md`。

## 路径 A — OpenAPI 真实 API 试点

### 1. 部署

```powershell
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am package -DskipTests
.\scripts\podman\deploy.ps1 -SkipBuild
```

### 2. 配置并导入

```powershell
Copy-Item .\scripts\pilot\pilot-openapi.config.example.json .\scripts\pilot\pilot-openapi.config.json
# 编辑：specPath 或 specUrl、operations（operationId、connectorId、sinkTable、cron）
.\scripts\pilot\setup-openapi-pilot.ps1
```

`serverUrlOverride` 可选：将解析出的 HTTP URL 主机替换为试点环境地址（保留 path）。

**本地 dry-run**（无真实 API，使用内置 Mock + `sample-users.json`）：

```powershell
.\scripts\pilot\setup-openapi-pilot.ps1 -ConfigPath .\scripts\pilot\pilot-openapi.config.mock-demo.json
```

Mock 端点：`GET /mock/e2e/v1/users`（与 OpenAPI 样例 path 一致）。

### 3. 每日观测

同路径 B，使用 `collect-daily-metrics.ps1`；报告填写 `docs/ops/pilot-report-openapi-2026-06.md`。

---

## 路径 B — Mock 观测演练

```powershell
# 仓库根目录
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am package -DskipTests
.\scripts\podman\deploy.ps1 -SkipBuild
```

确认：`curl http://localhost:8080/actuator/health` 返回 UP。

## 2. 创建并发布试点连接器

```powershell
.\scripts\pilot\setup-mock-pilot.ps1
```

将创建并发布：

| connector_id | 模板 | Cron | Mock |
|--------------|------|------|------|
| `pilot-mock-pagination` | rest-pagination | `0 0/15 * * * ?` | `/mock/e2e/pagination-items` |
| `pilot-mock-offset-limit` | rest-offset-limit | `0 5/15 * * * ?` | 同上 |
| `pilot-mock-cursor` | rest-cursor | `0 10/15 * * * ?` | `/mock/e2e/cursor-items` |
| `pilot-mock-kafka` | rest-kafka | `0 15/15 * * * ?` | `/mock/e2e/kafka-users` |
| `pilot-mock-monotonic-id` | rest-monotonic-id | `0 20/15 * * * ?` | `/mock/e2e/monotonic-items` |
| `pilot-mock-rolling-window` | rest-rolling-window | `0 25/15 * * * ?` | `/mock/e2e/window-items` |
| `pilot-mock-jiadu` | Jiadu Push | 无 Cron | `POST /mock/jiadu/push/{id}` |

脚本会触发一次全量同步，并发送 3 轮 Jiadu 模拟推送作为基线。

## 3. 每日观测（连续 7 天）

```powershell
.\scripts\pilot\collect-daily-metrics.ps1
```

指标追加到 `data/pilot-metrics-YYYY-MM.csv`。同时在 Admin UI 查看各连接器 **运行历史**。

## 4. 结束

填写 `docs/ops/pilot-report-2026-06.md`，完成 NF 勾选与 Go/No-Go。

## 清理

```powershell
.\scripts\podman\teardown.ps1
```
