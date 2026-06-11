# HTTP Ingestion Service — MVP 试点 + Phase 1.5 + 工程质量 路线图

> 日期：2026-06-06  
> 状态：**已评审（2026-06-06）**  
> 基线设计：`2026-06-05-http-ingestion-service-design.md`（v2）  
> 路线：**方案 1（Wave 1 → 2 → 3，约 7–8 周）**  
> 接口依据：《推理平台接口-260319.docx》§2.2.3 订阅事件推送 + **§3 对象定义**

> **2026-06-10 产品更新（P0-1）**：内置大华/美亚/佳都 **Pull 模板已移除**；当前为 **4 个 example 模板**（`rest-pagination` 等）+ **OpenAPI 导入**。下文「四套厂商 Pull 模板 / 大华美亚试点」为 Wave 1–2 **历史决策**，Mock 端点仍保留供回归。新试点标准见 README「快速开始：OpenAPI 导入」；`docs/ops/pilot-runbook.md` 将在 P0-8 重写。

---

## 1. 已确认决策

| # | 决策 | 说明 |
|---|------|------|
| 1 | **方案 1** | 先稳（CI/文档/试点）→ 7 天观测 → Phase 1.5 扩展；**Webhook 在 Wave 3** |
| 2 | **试点源** | **大华 + 美亚四套模板全部纳入**（vehicle query/count、traffic police、dispatch110） |
| 3 | **B 优先级** | Webhook > fixed_rate/调度 UI > offset/limit > Kafka |
| 4 | **CI** | **每次 PR 跑 Playwright**；Pull 侧对大华/美亚 Mock **多轮**自动化回归 |
| 5 | **Push 真实契约** | 佳都《推理平台接口-260319》**§2.2.3**（消息体/返回）+ **§3 对象定义**（`EventInfo` / `ResultInfo`） |
| 6 | **部署** | **Podman 一键部署**；应用容器 **挂载 jar**（不构建应用镜像） |
| 7 | **sign 校验** | **默认关闭**（`webhook.verify_sign=false`）；E2E 单独覆盖 `verify_sign=true` 子集 |
| 8 | **Podman 应用** | 使用 `eclipse-temurin:21-jre` + volume 挂载 `http-ingestion-service.jar` |

---

## 2. 总体目标

在 **7–8 周**内同时达成：

1. **A**：四套 Pull 连接器在真实/准生产 PG 上完成 **7 天试点**与 NF 验收  
2. **B**：实现佳都风格 **Webhook Push 接收**、调度补全、offset/limit 分页  
3. **C**：**PR 级 CI**（Maven + Playwright + 多轮集成/ Mock 回归）、文档与 Podman 交付物对齐

**Go 标准（整体）**

- [ ] NF 验收清单（`mvp-stories.md`）除明确 Phase 2 项外 100%  
- [ ] CI：Maven test + Playwright 全绿（PR 门禁）  
- [ ] 大华/美亚四套 Pull：**多轮** E2E（全量→增量→追加数据→增量）稳定  
- [ ] 佳都 Push：**多轮**模拟推送 E2E + Playwright 冒烟  
- [ ] `scripts/podman/deploy.*` 一键拉起可访问 UI + API + PG  
- [ ] 试点报告 `docs/ops/pilot-report-YYYY-MM.md` 与 Go/No-Go

---

## 3. 分波次计划

### Wave 1（第 1–2 周）— 工程质量 + 试点启动

| 工作项 | 交付 |
|--------|------|
| **C-CI** | GitHub Actions / 等价流水线：`mvn test` + `npm run test:e2e`（PR 必跑） |
| **C-文档** | 重写 `README.md`、API 表、测试矩阵 `docs/testing/test-matrix.md` |
| **C-Podman v1** | `deploy/podman-compose.yml` + `scripts/podman/deploy.ps1` / `deploy.sh` |
| **A-准备** | 四套连接器 publish 配置、PG 表 DDL、Prometheus scrape 示例 |
| **Mock 增强** | 大华/美亚 Mock 支持「多轮追加数据」API（测试专用 seed/append） |

**Wave 1 出口**：本地/Podman 一键部署成功；CI 绿；至少 1 套大华 + 1 套美亚多轮 Java E2E 通过。

### Wave 2（第 3–4 周）— 7 天试点 + 调度补全

| 工作项 | 交付 |
|--------|------|
| **A-试点** | 四套 Pull 同时 Cron 运行，每日记录 `ingestion_*` 指标 |
| **A-NF** | 对照 `mvp-stories.md` 勾验；备份恢复演练 |
| **B2-调度** | `fixed_rate` + UI 暂停/恢复 Quartz Job |
| **Playwright 扩展** | 四套模板 UI 创建/发布/全量/增量（Mock URL）多轮场景 |

**Wave 2 出口**：7 天试点报告；调度 UI 可用；Playwright 覆盖四套 Pull 主流程。

### Wave 3（第 5–7 周）— Webhook（佳都 Push）+ 分页扩展

| 工作项 | 交付 |
|--------|------|
| **B1-Webhook** | `/ingress/{connectorId}` 接收佳都 EventInfo 推送 |
| **B1-Mock 推送端** | `MockJiaduPushSimulator`：按文档构造 EventInfo + 可选 sign |
| **B1-模板** | `jiadu-event-push` 连接器模板（push 模式） |
| **B3-分页** | `offset/limit` 策略 + REST 模板 |
| **多轮 Push E2E** | N 轮推送 → PG 行数/幂等；失败重试语义（响应 1 分钟内） |
| **B4-Kafka** | **可选/Wave 4**：不阻塞 Go |

**Wave 3 出口**：佳都 Push 多轮自动化全绿；offset/limit E2E 通过。

---

## 4. Track A — 大华/美亚四套 Pull 试点

### 4.1 试点连接器清单

| 模板 ID | 目标表 | 模式 |
|---------|--------|------|
| `dahua-vehicle-query` | `dahua_vehicle_pass` | Pull + 增量 |
| `dahua-vehicle-count` | `dahua_vehicle_count` | Pull 全量 |
| `meiya-traffic-police-alert` | `meiya_traffic_police_alert` | Pull + 增量 |
| `meiya-dispatch110-flow` | `meiya_dispatch110_flow` | Pull + 增量 |

### 4.2 试点环境

- **业务 PG**：Podman compose 内 PostgreSQL 16（与 `PgTestSupport` DDL 一致）或客户提供的 PG  
- **源**：Wave 1–2 可用 **内置 Mock**（`/mock/dahua/*`、`/mock/meiya/*`）；Wave 2 可选切换真实 ITS 地址（仅改连接器 URL）  
- **调度**：Cron 默认 `0 0/5 * * * ?`，试点期可改为 `0 0/15 * * * ?` 降低压力  

### 4.3 7 天观测

- Prometheus：`ingestion_job_total`、`ingestion_job_duration_seconds`、`ingestion_watermark_lag_seconds`（按 connector_id）  
- 告警：连续 3 次 failed（见 `docs/ops/monitoring.md`）  
- 日报字段：成功率、records_ok、最大 lag、人工介入次数  

### 4.4 试点报告模板

路径：`docs/ops/pilot-report-YYYY-MM.md`  

含：环境说明、四套连接器配置摘要、7 日指标表、 incident 列表、Go/No-Go 建议。

---

## 5. Track B — Phase 1.5（含佳都 Push）

### 5.1 佳都接口契约（§2.2.3 + §3 对象定义）

**文档引用**

| 章节 | 用途 |
|------|------|
| **§2.2.3 订阅事件推送** | HTTP 方法、URI、消息体/返回类型、请求头、超时重试 |
| **§3 对象定义 → EventInfo** | 推送 **请求体** 字段、类型、必填 |
| **§3 对象定义 → ResultInfo** | 推送 **响应体** 字段（非通用 `ResponseResult`） |
| **§3 对象定义 → EventRegQo** | 订阅注册参考（MVP 不实现客户端，运维/佳都侧配置 `HttpAddressMaster`） |

**§2.2.3 接口摘要**

| 项 | 值 |
|----|-----|
| URI | 订阅注册时填写的 `HttpAddressMaster` → 本服务 `http://{host}:{port}/ingress/{connectorId}` |
| 方法 | POST |
| 查询字符串 | 无 |
| **消息体** | **`EventInfo`**（JSON） |
| **返回结果** | **`ResultInfo`**（JSON） |
| Content-Type | application/json |
| 请求头 | `x_request_id`；`sign` = MD5(`platFlag` + `_` + `EventID`) 32 位大写 |
| SLA | 响应 **≤ 1 分钟**；超时平台 **重试** |

> **字段名注意**：§2.2.3 请求示例使用 `ImageUrl`，§3 `EventInfo` 正式字段为 **`ImgUrl`**。Ingress 实现须 **同时接受** `ImgUrl` 与 `ImageUrl`（归一化写入 PG 列 `img_url`）。

---

#### 5.1.1 EventInfo（§3 — 推送请求体）

| 字段 | 类型 | 必填 | 说明 | MVP 落库 |
|------|------|------|------|----------|
| EventID | String | 是 | 事件 ID，唯一 | `event_id` PK |
| EventType | Int | 是 | 事件算法 ID | `event_type` |
| EventName | String | 是 | 事件算法名称 | `event_name` |
| SendTime | String | 是 | 推送时间（推送时才有值） | `send_time` |
| CameraID | String | 是 | 摄像头点位编号 | `camera_id` |
| ImgUrl | String | 是 | 报警原图 HTTP 地址 | `img_url` |
| VideoUrl | String | 否 | 报警录像 HTTP 地址 | `video_url` |
| EventTime | String | 是 | 事件创建时间 | `event_time` |
| EventBeginTime | String | 否 | 事件起始时间 | `event_begin_time` |
| EventEndTime | String | 否 | 事件结束时间 | `event_end_time` |
| Confidence | Float | 是 | 事件可信度 | `confidence` |
| TaskID | String | 是 | 视频分析任务号 | `task_id` |
| EventGroup | Int | 是 | 1=告警 2=统计 | `event_group` |
| Census | Int | 是 | 检出目标数量 | `census` |
| SubLis | Array | 否 | 目标位置列表（含 X/Y/Width/Height 等） | JSON 列 `sub_lis` |
| ObjectDescribe | String | 否 | 目标描述 | `object_describe` |
| ObjectImgUrl | String | 否 | 目标检出图 | `object_img_url` |
| MultAlarmObj | Array | 否 | 多告警对象 | JSON 列 `mult_alarm_obj` |
| HumanClass | Object | 否 | 客流分析 | JSON 列 |
| InterDay | Int | 是 | 跨天累计标志 0/1 | `inter_day` |
| EnterNumber | Int | 是 | 进入人数 | `enter_number` |
| OutNumber | Int | 是 | 离开人数 | `out_number` |
| DensityClass | Object | 否 | 密度类 | JSON 列 |

MVP Transform 默认映射 **加粗** 列；其余字段保留在 `raw_json` 或 JSON 列，便于排错。

**§2.2.3 请求示例（精简）**

```json
{
  "EventID": "UUID00001",
  "EventType": 3001,
  "EventName": "站台B柱侧面2",
  "CameraID": "国标编号",
  "ImgUrl": "http://example/alarm.jpg",
  "EventTime": "2024-06-06 12:12:12",
  "EventGroup": 1,
  "Confidence": 0.95,
  "TaskID": "TASK-001",
  "Census": 1,
  "InterDay": 0,
  "EnterNumber": 0,
  "OutNumber": 0,
  "SendTime": "2024-06-06 12:12:15"
}
```

---

#### 5.1.2 ResultInfo（§3 — 推送响应体）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| OpCode | int | 是 | 0=成功，其他=异常 |
| OpDesc | String | 是 | 结果描述 |

**§2.2.3 返回示例**

```json
{
  "OpCode": 0,
  "OpDesc": "成功"
}
```

Ingress **必须**返回上述结构（HTTP 200）；业务失败时仍返回 JSON，`OpCode != 0`，并写 `job_run` failed。

> 区别于通用 **`ResponseResult`**（含 `Data`、`StatusCode`、`BusinessCode` 等）：**订阅事件推送的响应类型是 `ResultInfo`，不是 `ResponseResult`**。

---

#### 5.1.3 EventRegQo（§3 — 订阅注册参考，仅文档）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| EventType | Array&lt;Int&gt; | 否 | 订阅事件列表，空=全部 |
| CameraID | Array&lt;String&gt; | 否 | 摄像头国标 ID 列表 |
| HttpAddressMaster | String | 是 | **主推送地址**（指向本服务 ingress） |
| HttpAddressStandby | String | 否 | 备推送地址 |
| SubBeginTime | String | 是 | `yyyy-MM-dd HH:mm:ss`，空=立即 |
| SubEndTime | String | 是 | 空=永久 |
| EventGroups | Array&lt;Int&gt; | 否 | 1=告警 2=统计 |

MVP：**不实现** `regSubscribe` 客户端；在 `docs/api/jiadu-push-integration.md` 说明如何向佳都注册 `HttpAddressMaster`。

---

### 5.2 Webhook Ingress 设计

```
POST /ingress/{connectorId}
  ├─ 校验 connector mode ∈ {push, both} 且已 publish
  ├─ 可选：校验 sign（**默认关闭**；`webhook.verify_sign` + `platFlag`）
  ├─ 解析 EventInfo（兼容 ImgUrl/ImageUrl）→ transform.input_root = $
  ├─ Transform → PG upsert（keys: event_id ← EventID）
  ├─ job_run type=push, status=success|failed
  └─ 响应 ResultInfo { OpCode, OpDesc }
```

**幂等与重试**

- 以 `EventID` 为 upsert 主键；佳都重试相同 EventID 不产生重复行  
- 处理超过 55s 记录 warn；仍须在 60s 内返回（异步写 Sink 可选：先 200 再后台写 — **Wave 3 实现时二选一，默认同步写以保证排错一致**）

**配置扩展（connector JSON）**

```json
{
  "webhook": {
    "enabled": true,
    "path_suffix": "",
    "verify_sign": false,
    "plat_flag": "ivsp"
  },
  "transform": {
    "input_root": "$",
    "steps": [{ "type": "map_fields", "mappings": [
      { "target": "event_id", "source": "$.EventID", "type": "string" },
      { "target": "event_type", "source": "$.EventType", "type": "long" },
      { "target": "event_time", "source": "$.EventTime", "type": "string" }
    ]}]
  },
  "sink": { "type": "postgresql", "target": { "table": "jiadu_event_info" }, "keys": ["event_id"] }
}
```

### 5.3 Mock 与多轮 Push 自动化

| 组件 | 职责 |
|------|------|
| `MockJiaduPushSimulator` | 测试/CI 内向 `/ingress/{id}` 发送 N 批 EventInfo |
| `MockJiaduPushStore` | 可配置事件池、EventID 序列、sign 生成 |
| **多轮场景** | Round1: 10 事件；Round2: 5 新 + 2 重复 EventID（幂等）；Round3: 大批量 100 |

**Java E2E**：`HttpIngestionE2ETest.JiaduPushIngress`（nested）  
**Playwright**：创建 push 连接器 → 触发 simulator API → 详情页 job 列表见 push 成功  

### 5.4 调度补全（B2）

- `schedule.type`: `cron` | `fixed_rate`（秒）  
- `ConnectorScheduleService`：fixed_rate → `SimpleScheduleBuilder`  
- UI：详情页「暂停调度 / 恢复调度」→ Quartz pause/resume  
- DB：`connector_schedule.enabled` 与 Quartz 状态一致  

### 5.5 offset/limit 分页（B3）

- 新策略 `offset_limit`：`offset_param` / `limit_param` / `page_start`  
- WireMock 多页 E2E + 模板 `rest-offset-limit`  

### 5.6 Kafka（B4，Wave 4 可选）

- 模块 `http-ingestion-sink-kafka`  
- 不纳入本次 Go 门禁  

---

## 6. Track C — 工程质量与 CI

### 6.1 CI 流水线（PR 门禁）

```yaml
jobs:
  backend:
    - mvn -pl http-ingestion-boot -am test
      -Dtest=HttpIngestionE2ETest,RequestBodyComposerTest,TransformPipelineTest,PostgreSql*
  ui-e2e:
    needs: backend
    - mvn -pl http-ingestion-boot -am package -DskipTests
    - cd http-ingestion-admin-ui && npm ci && npx playwright install chromium
    - E2E_SERVER_PORT=18080 npm run test:e2e
```

**前置**：Runner 安装 Podman（或 Docker）+ JDK 21。

### 6.2 多轮自动化测试策略

#### Pull — 大华/美亚（Java + Playwright）

| 轮次 | 操作 | 断言 |
|------|------|------|
| R1 | 全量 sync | PG 行数 = Mock 初始集 |
| R2 | Mock append 新记录 | 增量 sync | 行数增加 |
| R3 | 再次 append + Cron/手动增量 | watermark 推进、无 failed job |
| R4 | 重置水位 + 全量 | 行数一致、state 清空后重建 |

四套连接器 **各跑 R1–R4**（Java E2E 参数化）；Playwright 至少覆盖 **dahua-vehicle-query + meiya-traffic-police** 各 R1–R2。

#### Push — 佳都 EventInfo

| 轮次 | 操作 | 断言 |
|------|------|------|
| P1 | 10 条推送 | PG=10, OpCode=0 |
| P2 | 2 条重复 EventID + 3 新 | PG=13（幂等） |
| P3 | 错误 sign（**仅 verify_sign=true 配置** 的子集测试） | OpCode≠0 或 HTTP 4xx + job failed |
| P4 | 慢响应模拟（<60s） | 不触发平台重试风暴（单测 Mock） |

### 6.3 文档交付

| 文件 | 内容 |
|------|------|
| `README.md` | 能力清单、构建、Podman 一键部署、测试命令 |
| `docs/testing/test-matrix.md` | 单测 / E2E / Playwright / 多轮场景矩阵 |
| `docs/api/jiadu-push-integration.md` | EventInfo/ResultInfo、regSubscribe 说明、sign 规则 |
| `CHANGELOG.md` | 按 Wave 记录 |

---

## 7. Podman 一键部署

### 7.1 目录结构（新增）

```
deploy/
  podman-compose.yml      # postgres + app（JRE 容器挂载 jar）
  init-pg.sql             # 含 jiadu_event_info
  .env.example
scripts/podman/
  deploy.ps1              # Windows 一键
  deploy.sh               # Linux/macOS 一键
  teardown.ps1 / .sh
  README.md
```

### 7.2 Compose 服务（**挂载 jar，不构建应用镜像**）

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `postgres` | `postgres:16-alpine` | 5432 | 挂载 `deploy/init-pg.sql` 初始化 |
| `http-ingestion` | `eclipse-temurin:21-jre-alpine` | 8080 | **volume 挂载** `http-ingestion-boot/target/http-ingestion-service.jar`；`command: java -jar /app/http-ingestion-service.jar`；env 指向 postgres；volume `./data:/data` 元库 |

**compose 片段（示意）**

```yaml
services:
  http-ingestion:
    image: eclipse-temurin:21-jre-alpine
    volumes:
      - ../http-ingestion-boot/target/http-ingestion-service.jar:/app/http-ingestion-service.jar:ro
      - ../data:/data
    command: ["java", "-jar", "/app/http-ingestion-service.jar"]
    environment:
      SERVER_PORT: 8080
      META_DB_PATH: /data
      EXTERNAL_PG_URL: jdbc:postgresql://postgres:5432/postgres
      EXTERNAL_PG_USER: postgres
      EXTERNAL_PG_PASSWORD: postgres
    ports:
      - "8080:8080"
    depends_on:
      - postgres
```

### 7.3 一键命令

```powershell
# Windows
.\scripts\podman\deploy.ps1
# 访问 http://localhost:8080
```

```bash
./scripts/podman/deploy.sh
```

**脚本行为**

1. 检查 podman / podman compose  
2. `mvn package -DskipTests`（`-SkipBuild` 可跳过，但 jar 必须存在）  
3. `podman compose -f deploy/podman-compose.yml up -d`（**无 `--build`**）  
4. 轮询 `/actuator/health` 至 UP  
5. 输出 UI URL、Mock 路径、PG 连接信息  

**teardown**：`podman compose down -v`（可选保留 volume）

### 7.4 与 E2E 关系

- Playwright `global-setup` 可复用 **相同 compose 文件**（`E2E_USE_COMPOSE=1`）替代「jar + 临时 PG」，缩短 CI 时间（Wave 1 后期优化项）。

---

## 8. 测试覆盖目标（完成后）

| 层级 | 目标 |
|------|------|
| 单元 | core engine + webhook sign 验证 + schedule normalize |
| Java E2E | 现有 11+ 用例 + 四套 Pull 多轮 + Jiadu Push 多轮 + fixed_rate + offset |
| Playwright | 5+ → **15+**（四套 Pull 冒烟 + Push + 调度 + EN locale） |
| 试点 | 7 天人工观测 + 报告 |

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| PR Playwright 耗时长（~5–10 min） | 并行 job；compose 复用；缓存 jar/npm |
| 佳都 sign 规则与文档不一致 | 对接前与佳都联调 1 次；sign 校验可配置关闭 |
| 四套同时试点资源争用 | Cron 错开分钟；PG 连接池限流 |
| Podman on Windows 路径/权限 | 文档注明 Podman Machine；提供 WSL2 指引 |
| Push 1 分钟超时 | 控制 batch 大小；Sink 批量 upsert；必要时异步 job 队列（Phase 1.5 末评估） |

---

## 10. 范围外（明确不做）

- Admin 登录、OAuth2、Webhook 鉴权强制开启  
- Kafka Sink（Wave 4  unless reprioritized）  
- 多 Sink 扇出、配置版本回滚  
- 佳都 regSubscribe 客户端（仅文档说明手工注册）  
- 多实例 / Quartz JDBC 集群  

---

## 11. 评审检查清单（Spec Self-Review）

- [x] 无 TBD：Wave 交付物均已列明  
- [x] 与用户 8 条决策一致（含 Wave 顺序、sign 默认关、挂载 jar）  
- [x] 佳都 Push：§2.2.3 + §3 `EventInfo` / `ResultInfo` 字段表完整  
- [x] `ImgUrl` / `ImageUrl` 兼容策略已写明  
- [x] CI 含 PR Playwright + 多轮 Mock  
- [x] Podman：JRE 容器 + jar volume，无应用镜像构建  
- [x] 范围边界清晰，Kafka 不阻塞 Go  

---

## 12. 下一步

1. ~~规格评审~~ **已通过（2026-06-06）**  
2. 使用 **writing-plans** 生成 Wave 1 起的 implementation plan  
3. Wave 1 首项：**CI 流水线 + Podman deploy（挂载 jar）+ README + test-matrix**
