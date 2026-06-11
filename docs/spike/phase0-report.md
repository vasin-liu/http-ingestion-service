# HTTP Ingestion Service — Phase 0 Spike 报告

> 日期：2026-06-05  
> 状态：已完成（基于 JSONPlaceholder 公开 API 验证）

## 目标

验证 TrialRequest / Pull 引擎对 REST 列表 API 的拉取能力，为分页/增量/Transform 提供样本响应。

## 验证环境

- JDK：Zulu 21.0.9
- 服务：`http://127.0.0.1:8080`
- 样本 API：`https://jsonplaceholder.typicode.com/users`

## 结果摘要

| 项 | 值 |
|----|-----|
| 内部 API URL | `https://jsonplaceholder.typicode.com/users`（公开替代） |
| 分页风格 | 无分页（单页数组）；生产源使用 page/page_size |
| 增量字段 | 样本 API 无 `updated_at`；配置骨架使用 `$.updated_at` |
| 建议 input_root | `$`（根数组） |
| Transform 映射 | `id` → long，`name` → string |
| 全量同步 | 100 条记录，status=success |
| 风险项 | 源站无真实增量字段时需关闭 incremental 或换 API |

## 试请求响应结构（节选）

```json
[
  { "id": 1, "name": "Leanne Graham", "email": "Sincere@april.biz", ... },
  { "id": 2, "name": "Ervin Howell", ... }
]
```

## JsonPath 建议

- `$.[*]` — 数组元素
- `$[0].id` — 首条 id
- `$[0].name` — 首条 name

## 结论

Pull → Transform 闭环在 MVP 环境可复现；接入真实内部 API 时需确认分页参数名与增量时间戳字段，并配置 `EXTERNAL_PG_*` 完成 PG upsert 验证。
