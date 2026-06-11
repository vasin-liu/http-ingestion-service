# 7 天试点运行手册

> 对应 Wave 2 Track A。报告模板：`docs/ops/pilot-report-template.md`

## 启动前

- [ ] `.\scripts\podman\deploy.ps1` 或客户 PG 就绪
- [ ] `deploy/init-pg.sql` 业务表已存在
- [ ] Prometheus scrape 配置（见 `docs/ops/prometheus-scrape.example.yml`）
- [ ] 四套连接器 publish 完成（Mock 或真实 URL）

## 连接器配置建议

| 模板 | Cron 建议 | Mock URL |
|------|-----------|----------|
| dahua-vehicle-query | `0 0/15 * * * ?` | `/mock/dahua/gretrieval/vehicle/query` |
| dahua-vehicle-count | `0 5/15 * * * ?` | `/mock/dahua/gretrieval/vehicle/count` |
| meiya-traffic-police-alert | `0 10/15 * * * ?` | `/mock/meiya/api/res/trafficPoliceAlert` |
| meiya-dispatch110-flow | `0 15/15 * * * ?` | `/mock/meiya/api/res/dispatch110Flow` |

Cron 错开分钟，降低 PG 与 HTTP 争用。

## 每日观测（7 天）

1. 打开 Admin UI 各连接器详情 → 运行历史成功率
2. `curl http://localhost:8080/actuator/prometheus | findstr ingestion_`
3. 记录：`ingestion_job_total`、`ingestion_watermark_lag_seconds`
4. 连续 3 次 failed → 按 `docs/ops/monitoring.md` 处理

## NF 验收

对照 `docs/superpowers/specs/2026-06-05-http-ingestion-mvp-stories.md` 逐项勾选。

## 备份恢复

按 `docs/ops/backup-restore.md` 演练一次元库 + PG 备份恢复。

## 结束

填写 `docs/ops/pilot-report-YYYY-MM.md`，给出 Go/No-Go。
