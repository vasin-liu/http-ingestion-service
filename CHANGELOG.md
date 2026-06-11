# Changelog

## [Unreleased]

### OpenAPI 导入（通用化）

- API：`POST /api/openapi/parse`（OAS3 + Swagger 2.0，URL 拉取最大 16MB）
- UI：OpenAPI 导入弹窗（搜索、多选、批量创建、向导导入）
- HTTP：Postman 式 Params/Headers/Body；树形 JSON Body / 响应 Schema 可视化
- 模板：内置厂商模板移除，保留 4 个 example 模板（`rest-pagination` 等）
- 测试：`OpenApiImportServiceTest`、`OpenApiImportE2ETest`（纳入 CI）
- Playwright：`openapi-import.spec.ts`（粘贴解析、搜索、向导、POST Body 树、编辑页 schema 持久化、批量创建）
- 向导：`openapi_meta` 往返（导入/保存/编辑刷新）、POST 默认展示 Body 树
- JSON 树形编辑器：`JsonTreeTable` 统一列宽/行高、object 内添加字段、展开/收起回归（`json-tree.spec.ts` + `jsonTree.test.ts`）
- 文档：README / test-matrix 与当前产品对齐

### Wave 4

- Sink: `http-ingestion-sink-kafka` module + `KafkaRecordSink` (JSON, message key from `keys[0]`)
- Core: `RecordSinkDispatcher` routes by `sink.type` (`postgresql` | `kafka`)
- Config: `ingestion.external-kafka.bootstrap-servers` / `EXTERNAL_KAFKA_BOOTSTRAP_SERVERS`
- Template: `rest-kafka`
- Health: `externalKafka` actuator indicator
- Tests: `KafkaRecordSinkTestcontainersTest`, `KafkaSinkE2ETest`, Playwright `kafka-sink.spec.ts`（纳入 CI）
- Policy: 此后新功能必须 Java E2E + Playwright UI（见 `AGENTS.md`）

### Wave 3

- Webhook: `POST /ingress/{connectorId}` + `PushIngressService`（同步写 PG）
- Jiadu: EventInfo 归一化（`ImageUrl`→`ImgUrl`）、可选 sign 校验（默认关）
- Templates: `jiadu-event-push`、`rest-offset-limit`
- Pagination: `offset_limit` 策略（query offset/limit）
- Mock: `MockJiaduPushSimulator`（`POST /mock/jiadu/push/{id}`）
- E2E: `JiaduPushIngress` P1–P4、`OffsetLimitPull` WireMock 3 页
- Playwright: `jiadu-push.spec.ts`
- Docs: 完整 `docs/api/jiadu-push-integration.md`

### Wave 2

- Schedule: `fixed_rate` + `interval_seconds` Quartz trigger
- API: `GET/POST .../schedule`, pause/resume
- UI: 调度状态展示、暂停/恢复按钮
- E2E: fixed_rate、pause/resume Java tests
- Playwright: 四套 Pull R1-R2、`connector-schedule.spec.ts`
- Ops: `docs/ops/pilot-runbook.md`

### Wave 1

- CI: GitHub Actions Maven + Playwright PR gate
- Deploy: Podman compose with JRE container + jar volume mount
- Mock: `/mock/_test` reset/append API (`e2e` profile)
- E2E: Pull multi-round R1–R4 (dahua query, meiya traffic-police)
- Docs: README, test matrix, jiadu push skeleton, ops templates
