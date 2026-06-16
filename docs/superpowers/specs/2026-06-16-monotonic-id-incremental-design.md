# monotonic_id 增量 — 设计规格

> 日期：2026-06-16  
> 状态：**已确认**  
> 主轴：能力扩展 → 分页与增量 → **monotonic_id**

## 目标

支持 `incremental.mode: monotonic_id`，按记录自增 ID 推进水位，增量请求携带 `since_id`（可配置）拉取新数据。

## 配置

```yaml
incremental:
  enabled: true
  mode: monotonic_id
  monotonic_id:
    response_path: "$.id"
    request_param: since_id
    request_target: query
```

水位 JSON：`{"last_id": "42"}`（数值或字符串）。

## 决策

- 与 timestamp 共用 `IncrementalSettings` 扁平字段 + `mode` 区分
- `WatermarkState`（timestamp + lastId）贯穿引擎与 SyncService
- 首跑策略仍由 `sync.on_first_run` 控制
