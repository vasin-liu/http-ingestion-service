# HTTP Ingestion Service — 监控与告警指南

> 版本：v1  
> 日期：2026-06-05  
> 适用：Phase 1 MVP（单实例）

---

## 1. 监控端点

| 端点 | 用途 |
|------|------|
| `GET /actuator/health` | 存活与依赖探针（H2、外部 PG） |
| `GET /actuator/prometheus` | Prometheus 指标拉取 |
| `scripts/status.sh` | 脚本级快速检查（PID + health） |

**健康探针（MVP）**

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "externalPg": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 2. 核心指标

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `ingestion_job_total` | Counter | `connector_id`, `status` | 任务计数（success/failed） |
| `ingestion_job_duration_seconds` | Histogram | `connector_id`, `type` | 任务耗时（full/incremental/sample） |
| `ingestion_records_processed_total` | Counter | `connector_id`, `stage` | 处理记录数（http/transform/sink） |
| `ingestion_watermark_lag_seconds` | Gauge | `connector_id` | 水位滞后秒数（now - watermark） |

**JVM / Spring Boot 标准指标**（`actuator/prometheus` 自带）建议一并采集：

- `jvm_memory_used_bytes`
- `process_cpu_usage`
- `http_server_requests_seconds`（API 延迟）

---

## 3. 告警规则

MVP 不内置通知渠道，通过 Prometheus / 脚本对接现有平台。

### 3.1 服务不可用

| 项 | 值 |
|----|-----|
| 条件 | `up{job="http-ingestion"} == 0` 持续 **1 分钟** |
| 或 | health 探针非 UP |
| 严重级别 | P1 |
| 处理 | 检查进程、`./data` 磁盘、日志；`restart` |

**PromQL 示例**

```promql
up{job="http-ingestion"} == 0
```

### 3.2 连续同步失败

| 项 | 值 |
|----|-----|
| 条件 | 同一 `connector_id` **连续 3 次** `status="failed"` |
| 严重级别 | P2 |
| 处理 | UI 查看 `job_run` 排错；检查源 API / PG 连通性 |

**PromQL 示例**（需应用侧在失败时递增 counter；或用 recording rule）

```promql
increase(ingestion_job_total{status="failed"}[30m]) >= 3
```

> 若单连接器 30 分钟内失败 ≥ 3 次即告警；可按调度频率调整窗口。

### 3.3 水位停滞

| 项 | 值 |
|----|-----|
| 条件 | `ingestion_watermark_lag_seconds > 21600`（**6 小时**）且调度未暂停 |
| 严重级别 | P2 |
| 处理 | 检查调度是否触发、源 API 是否有新数据、任务是否卡住 |

**PromQL 示例**

```promql
ingestion_watermark_lag_seconds > 21600
```

### 3.4 H2 / PG 探针失败

| 项 | 值 |
|----|-----|
| 条件 | health 组件 `db` 或 `externalPg` 为 DOWN |
| 严重级别 | P1（db）/ P2（externalPg） |
| 处理 | db DOWN → 检查 H2 文件与磁盘；PG DOWN → 检查连接串与网络 |

### 3.5 磁盘空间不足

| 项 | 值 |
|----|-----|
| 条件 | `./data` 所在分区使用率 > **85%** |
| 严重级别 | P2 |
| 处理 | 清理日志、归档 `job_run_detail`、扩容卷 |

---

## 4. Prometheus 抓取配置示例

```yaml
scrape_configs:
  - job_name: http-ingestion
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["http-ingestion-host:8080"]
    scrape_interval: 30s
```

---

## 5. 无 Prometheus 时的轻量巡检

可用 cron 每 5 分钟执行：

```bash
#!/bin/bash
# /etc/cron.d/http-ingestion-check
*/5 * * * * deploy /opt/http-ingestion/scripts/status.sh || echo "ALERT: http-ingestion down" | mail -s "ingestion alert" ops@example.com
```

---

## 6. 值班 Runbook 速查

| 告警 | 第一步 | 第二步 |
|------|--------|--------|
| 服务 DOWN | `status.sh` → 看日志 tail | `restart.sh`；仍失败查 H2 损坏 |
| 连续失败 | UI 打开失败 `job_run` | 试请求源 API；查 PG 连接 |
| 水位停滞 | 确认 Cron 未暂停 | 手动「立即增量」；看是否卡住 |
| PG 探针 DOWN | `psql` 测连通 | 检查 `EXTERNAL_PG_URL` 与防火墙 |

---

## 7. 相关文档

- 备份恢复：`docs/ops/backup-restore.md`
- 启停脚本：`scripts/README.md`
