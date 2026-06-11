# HTTP Ingestion Service — Sprint 2 任务拆解

> 周期：第 3–4 周  
> 目标：**Pull → Transform → PG upsert 闭环 + Cron 调度**  
> 对应故事：US-2.3、US-2.4、US-3.1、US-3.2、US-4.1–4.3、US-3.3、US-3.6

## Sprint 2 完成标准

- [x] page/page_size 分页拉取
- [x] timestamp 增量水位推进
- [x] filter / map_fields / expression Transform
- [x] PostgreSQL upsert Sink
- [x] 手动全量/增量同步 + 运行历史
- [x] Cron 调度（发布时注册 Quartz）
- [x] JsonPath 点选 + 映射预览 API/UI

## 关键 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/connectors/{id}/sync?type=full\|incremental` | 触发同步 |
| GET | `/api/connectors/{id}/jobs` | 运行历史 |
| GET | `/api/connectors/{id}/state` | 增量水位 |
| POST | `/api/connectors/{id}/state/reset` | 重置水位 |
| POST | `/api/preview/transform` | 映射预览 |
| POST | `/api/preview/jsonpath` | JsonPath 建议 |

## 配置 JSON 示例

```json
{
  "http": { "method": "GET", "url": "https://api.example.com/items", "timeout_ms": 30000 },
  "pagination": {
    "strategy": "page_page_size",
    "page_param": "page",
    "page_size_param": "page_size",
    "page_size": 100,
    "total_count": { "json_path": "$.meta.total" }
  },
  "incremental": {
    "enabled": true,
    "timestamp": {
      "response_path": "$.updated_at",
      "request_param": "updated_after",
      "overlap": "5m"
    }
  },
  "transform": {
    "input_root": "$.data",
    "steps": [
      { "type": "map_fields", "mappings": [{ "target": "id", "source": "$.id", "type": "long" }] }
    ]
  },
  "sink": {
    "type": "postgresql",
    "target": { "schema": "public", "table": "items" },
    "keys": ["id"],
    "write_mode": "upsert"
  },
  "schedule": { "enabled": true, "type": "cron", "expression": "0 0/5 * * * ?" }
}
```

## 验证步骤

1. 配置 `scripts/env` 中 `JAVA_HOME` 与 `EXTERNAL_PG_*`
2. 在 PG 中创建目标表（列名与映射字段一致）
3. 发布连接器 → 立即全量 → 查看 `/jobs` 与 PG 数据
4. 再次增量同步 → 水位更新
