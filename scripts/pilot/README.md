# 7 天 Mock 试点脚本

路径 B（示例模板 + 内置 Mock），对应 `docs/ops/pilot-runbook.md`。

## 1. 部署

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
