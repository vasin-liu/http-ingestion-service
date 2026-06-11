# 测试矩阵

> PR 门禁：Java E2E + Playwright UI。**此后每个新功能**必须同时补齐两层测试（见 `AGENTS.md`、`.cursor/rules/e2e-testing-required.mdc`）。

## 层级总览

| 层级 | 位置 | 前置 | 命令 |
|------|------|------|------|
| 单元 | `http-ingestion-core`, sink 模块 | JDK 21 | 见下表 |
| Java E2E | `HttpIngestionE2ETest`, `*E2ETest` | Podman/Docker + Testcontainers | 见 CI 命令 |
| Playwright | `http-ingestion-admin-ui/e2e` | Podman/Docker、预构建 jar | `npm run test:e2e` |
| CI | `.github/workflows/ci.yml` | GitHub ubuntu + podman | PR 自动 |

## 单元测试

| 类 | 覆盖 |
|----|------|
| `RequestBodyComposerTest` | HTTP body 占位符、增量时间注入、**offset_limit**、**cursor body** |
| `CursorPaginationSupportTest` | cursor `stop_when` OR 语义 |
| `JsonPathSupportCursorTest` | cursor/hasMore JsonPath 读取 |
| `JiaduSignVerifierTest` | 佳都 sign MD5 |
| `KafkaRecordSinkTestcontainersTest` | Kafka JSON publish + message key |
| `TransformPipelineTest` | map_fields、类型转换 |
| `OpenApiImportServiceTest` | OAS3 + Swagger2 解析、Schema 提取 |
| `PostgreSqlRecordSinkTestcontainersTest` | PG upsert |
| `PostgreSqlVersionCompatibilityTest` | PG 版本兼容 |

## Java E2E

| 测试类 / Nested | 场景 |
|-----------------|------|
| `HttpIngestionE2ETest.ConnectorLifecycle` | CRUD、publish、reset state |
| `HttpIngestionE2ETest.MockSourceApi` | Mock 源端点冒烟 |
| `HttpIngestionE2ETest.MockTestApi` | `/mock/_test` reset/append |
| `HttpIngestionE2ETest.PreviewApi` | trial、jsonpath、sample 不写 Sink |
| `HttpIngestionE2ETest.GenericPullSync` | WireMock GET 全量 |
| `HttpIngestionE2ETest.IntegrationSync` | 四套 Pull 全量+增量 |
| `HttpIngestionE2ETest.SchedulerSync` | Quartz Cron / **fixed_rate** / pause/resume |
| `HttpIngestionE2ETest.PullMultiRound` | R1–R4 多轮 |
| `HttpIngestionE2ETest.JiaduPushIngress` | 佳都 Push P1–P4 + Mock simulator |
| `HttpIngestionE2ETest.OffsetLimitPull` | WireMock offset/limit 3 页 6 行 |
| `HttpIngestionE2ETest.CursorPull` | WireMock query cursor 2 页 3 行 |
| `KafkaSinkE2ETest` | WireMock Pull → Kafka topic（**CI 门禁**） |
| `OpenApiImportE2ETest` | `POST /api/openapi/parse`（OAS3 内联、Swagger2 URL）、批量创建 + `openapi_meta` |

### Pull 多轮（R1–R4）

| 轮次 | 操作 | 断言 |
|------|------|------|
| R1 | 全量 sync | PG 行数 = Mock 初始集 |
| R2 | append + 增量 | 行数增加 |
| R3 | 再次 append + 增量 | 行数再增 |
| R4 | reset 水位 + 全量 | 行数与 R3 终态一致 |

## Playwright UI E2E

| Spec | 覆盖 |
|------|------|
| `theme.spec.ts` | 明暗主题、语言切换 |
| `connector-list.spec.ts` | 列表、模板入口（含 **rest-kafka**） |
| `connector-flow.spec.ts` | 向导创建、发布、同步 |
| `pull-multi-round.spec.ts` | 四套 Pull 模板 R1 全量 + R2 增量 |
| `connector-schedule.spec.ts` | 调度暂停/恢复 |
| `jiadu-push.spec.ts` | 佳都 Push 模板 + simulator |
| `kafka-sink.spec.ts` | **rest-kafka** 向导 → 全量 sync → Kafka 消息数 |
| `openapi-import.spec.ts` | OpenAPI 导入全链路（粘贴解析、搜索、GET Params、POST Body 树、编辑页 schema 刷新、响应 Schema、批量创建） |
| `json-tree.spec.ts` | JSON 树形 Body 编辑器（嵌套 object/array、展开/收起行数稳定、object 内添加字段） |
| `cursor-pagination.spec.ts` | **rest-cursor** 模板 → Mock 全量 sync |

Playwright `global-setup.ts` 启动 **PostgreSQL + Kafka**（Podman/Docker），并注入 `EXTERNAL_KAFKA_BOOTSTRAP_SERVERS`。

Admin UI 单元测试（Vitest）：`cd http-ingestion-admin-ui && npm run test:unit`（`jsonTree.test.ts`：flatten 无 `children`、展开行数稳定）

环境变量：`E2E_SERVER_PORT`（默认 18080）、`E2E_SKIP_STACK=1`（连接已有服务）。

## CI 门禁

```bash
# backend
./mvnw -pl http-ingestion-boot -am test \
  -Dtest=HttpIngestionE2ETest,OpenApiImportE2ETest,JiaduSignVerifierTest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql*,KafkaSinkE2ETest,OpenApiImportServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# ui-e2e（backend 通过后）
./mvnw -pl http-ingestion-boot -am package -DskipTests
cd http-ingestion-admin-ui && npm ci && npm run test:e2e
```

失败时上传 `playwright-report` artifact。

## 新功能交付清单

- [ ] 单元测试（核心逻辑）
- [ ] Java E2E（Testcontainers / WireMock / Mock API）
- [ ] Playwright 主路径 UI 自动化
- [ ] 本文件矩阵已更新
