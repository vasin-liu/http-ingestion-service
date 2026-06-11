# HTTP Ingestion Service — Sprint 3 任务拆解

> 周期：第 5–6 周  
> 目标：**能排错、能运维、MVP 收尾**  
> 对应故事：US-3.4、US-3.5、US-5.1–5.3、US-1.5、US-2.5

## Sprint 3 完成标准

- [x] 采样试运行（limit、可选不写 Sink、run_type=sample、不推进水位）
- [x] 运行历史增强（耗时、分页、失败详情抽屉）
- [x] 逐步排错追踪（job_run_detail API、HTTP/Transform/Sink 阶段样本）
- [x] Prometheus 业务指标（job_total、duration、records、watermark_lag）
- [x] 内置连接器模板（REST 分页、Webhook 数组）
- [x] 水位重置二次确认 + 审计记录
- [x] Phase 0 spike 报告归档
- [ ] NF 验收清单（需真实内部源 7 天试点）

## 关键 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/connectors/{id}/sync?type=sample&limit=10&writeSink=false` | 采样试运行 |
| GET | `/api/connectors/{id}/jobs/{jobId}/details` | 排错详情（最多 20 条错误样本） |
| GET | `/api/templates` | 内置模板列表 |
| GET | `/actuator/prometheus` | 含 `ingestion_*` 业务指标 |

## 验证步骤

1. 详情页触发采样同步（limit=10, writeSink=false）→ job 类型 sample、水位不变
2. 故意配置错误映射 → 查看 job 详情中的 transform 错误样本
3. `curl /actuator/prometheus | grep ingestion_` 确认指标存在
4. 从模板创建连接器 → 向导预填分页/增量/Sink 骨架

## Testcontainers 集成测试（Pull → PG）

**前置**：Docker 或 Podman（`podman machine start`）

```powershell
$env:JAVA_HOME = "E:\Home\vasin.GENSOKYO\sdk\zulu-jdk21.0.9"
mvn -pl http-ingestion-sink-pg,http-ingestion-boot -am test `
  "-Dtest=PostgreSqlRecordSinkTestcontainersTest,PullToPostgreSqlTestcontainersTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

| 测试类 | 验证内容 |
|--------|----------|
| `PostgreSqlRecordSinkTestcontainersTest` | PG upsert 插入 + 冲突更新 |
| `PullToPostgreSqlTestcontainersTest` | WireMock HTTP → Transform → PG 全链路 |
