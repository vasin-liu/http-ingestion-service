# rolling_window 增量 — 设计规格

> 日期：2026-06-16  
> 状态：**已确认**

## 目标

支持 `incremental.mode: rolling_window`，按固定时间窗口（`startTime` / `endTime`）拉取数据，适合只接受时间范围、不支持单点 `since` 水位的 API。

## 配置

```yaml
incremental:
  enabled: true
  mode: rolling_window
  rolling_window:
    response_path: "$.updated_at"
    start_param: startTime
    end_param: endTime
    request_target: query
    format: iso_instant
    overlap: 5m
```

Body 模式使用 `request_body_start_path` / `request_body_end_path`。

## 行为

| 阶段 | 请求 | 水位 |
|------|------|------|
| 全量 | 不注入窗口参数 | 记录中 `response_path` 最大时间戳 |
| 增量 | `start = watermark - overlap`，`end = now + 1m` | 推进为本次窗口 `end` |

与 `timestamp` 模式的区别：增量请求使用 **起止双参数**，水位按窗口右边界滚动，而非单点 `updated_after`。

## 决策

- 复用 `IncrementalSettings` + `WatermarkState.timestamp`
- 全量首跑仍由 `sync.on_first_run` 控制
- `monotonic_id` 与 `timestamp` 行为不变
