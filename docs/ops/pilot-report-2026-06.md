# 试点报告 — 2026-06（Mock 路径 B · 试运行）

> 试点类型：**Mock 观测演练**（示例模板 + 内置 Mock）  
> 试运行日期：**2026-06-11**  
> 手册：`docs/ops/pilot-runbook.md` · 脚本：`scripts/pilot/README.md`

## 环境

| 项 | 值 |
|----|-----|
| 部署方式 | Podman compose（`scripts/podman/deploy.ps1`） |
| Spring profile | `e2e` |
| 业务 PG | PostgreSQL 16 |
| Kafka | compose 内 `kafka:9092` |
| 应用 | `http://localhost:8080` |

## 连接器清单

| connector_id | 模板 | Sink | Cron |
|--------------|------|------|------|
| `pilot-mock-pagination` | rest-pagination | PG `items` | `0 0/15 * * * ?` |
| `pilot-mock-offset-limit` | rest-offset-limit | PG `items` | `0 5/15 * * * ?` |
| `pilot-mock-cursor` | rest-cursor | PG `items` | `0 10/15 * * * ?` |
| `pilot-mock-kafka` | rest-kafka | Kafka `ingest.items` | `0 15/15 * * * ?` |
| `pilot-mock-jiadu` | Jiadu Push | PG `jiadu_event_info` | 模拟推送 |

## 试运行结论

| 连接器 | 结果 | 说明 |
|--------|------|------|
| pilot-mock-pagination | 通过 | 全量 sync + Cron 调度正常 |
| pilot-mock-offset-limit | 通过* | 需 `input_root=$.data` 适配 Mock 信封（`setup-mock-pilot.ps1` 已处理） |
| pilot-mock-cursor | 通过 | 2 页 cursor 全量写入 PG |
| pilot-mock-kafka | 通过 | 记录写入 Kafka topic |
| pilot-mock-jiadu | 通过 | 模拟器 3 轮推送成功 |

\* `rest-offset-limit` 模板默认根数组响应；与 `pagination-items` Mock 联用时需在向导或脚本中设 `input_root=$.data`。

## Incident

| 时间 | 连接器 | 现象 | 处理 | 根因 |
|------|--------|------|------|------|
| 06-11 | pilot-mock-offset-limit | 全量失败，PG null id | setup 脚本补 `input_root=$.data` | 模板与 Mock 响应形状不一致 |

## NF 验收（Mock 试运行范围）

- [x] Podman 一键部署 + actuator UP（含 externalPg / externalKafka）
- [x] 向导模板创建、发布、手动/调度同步
- [x] 三种 Pull 分页（page / offset / cursor）+ Kafka Sink + Jiadu Push
- [x] Prometheus `ingestion_*` 指标可采集
- [ ] 完整 7 日连续观测（本次为试运行，未执行）
- [ ] 备份恢复演练（未执行）

## Go / No-Go

| 项 | 内容 |
|----|------|
| **结论** | **Go**（Mock 试运行） |
| **遗留项** | offset-limit 模板与信封型 Mock 的文档说明；完整 7 日报告可选补做 |
| **建议** | 进入 **Phase 1.5 能力扩展**：Link/Header 分页 或 monotonic_id 增量；有真实 API 时可做 runbook 路径 A 第二轮 |
