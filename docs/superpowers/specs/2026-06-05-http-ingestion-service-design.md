# HTTP Ingestion Service — 设计规格

> 产品代号：`http-ingestion-service`  
> 日期：2026-06-05  
> 状态：已修订（v2）  
> 定位：内部部署的 HTTP 协议数据摄取服务（Pull + Webhook Push）

### 命名说明

采用 **`{protocol}-{function}-service`** 命名规范，诚实标注当前能力边界为 **HTTP-only**：

| 组成部分 | 含义 |
|----------|------|
| **http** | 唯一支持的源协议：HTTP 拉取 + HTTP Webhook 推送 |
| **ingestion** | 数据摄取（Data Ingestion），国际数据工程通用术语 |
| **service** | 可独立部署的内部微服务 |

与 Airbyte「HTTP Connector」、云厂商「API / HTTP Ingestion」语境一致。日后若扩展 JDBC、S3 等，可并列新产品（如 `jdbc-ingestion-service`）或升格为平台级 `ingestion-service` 父品牌。

| 概念 | 本产品中对应 |
|------|-------------|
| **HTTP Connector** | 可配置的 HTTP 数据源（含 Webhook） |
| **Sink / Destination** | PostgreSQL、Kafka 扇出目标 |
| **Sync** | 全量 / 增量同步 |

目录、Maven 坐标、镜像、jar 统一使用 kebab-case：`http-ingestion-service`。

---

## 1. 背景与目标

构建一个 **HTTP 协议**的可配置数据摄取服务，支持：

- **拉取（Pull）**：HTTP 全量与增量；可配置分页策略；总数可来自分页响应或独立 count 接口
- **条件**：固定条件、滚动刷新、自定义组合
- **推送（Push）**：第三方 HTTP Webhook 主动推送
- **扇出（Sink）**：写入现有 Kafka 与 PostgreSQL（业务库，非元数据库）
- **零代码配置**：映射、调度、分页、增量均在 UI 完成

### 1.1 产品定位（一句话）

> **面向内部 API 与 Webhook 的可配置数据管道，替代 Cron + 脚本拉数，数据稳定落地到现有 PG/Kafka。**

### 1.2 目标用户

| 角色 | 职责 | 阶段优先级 |
|------|------|------------|
| **数据工程师**（主用户） | 创建连接器、配置映射与 Sink、排错、管理水位 | P0 |
| **运维**（次用户） | 部署、启停、备份、健康检查、告警 | P0 |
| **业务配置员**（可选） | 在模板基础上微调简单连接器 | P1（简单模式上线后） |

**替代对象**：手写 Python/curl 拉数脚本、分散的 Cron 任务、手工 Kafka 投递。

### 1.3 成功指标（MVP 验收）

| 指标 | 目标 |
|------|------|
| 新源接入耗时 | 熟悉 API 的数据工程师 **< 2 小时**完成首次成功同步 |
| 同步可靠性 | 单连接器日同步成功率 **> 99%**（排除源站故障） |
| 自助排错 | 失败后可在 UI 定位到**第几页 / 哪条记录 / 哪一步**，无需翻服务端日志 |
| 部署复杂度 | 单 jar + 启停脚本，**零外部元数据库**即可运行 |
| 运维可恢复 | 元库备份恢复有文档，演练通过 |

### 1.4 已确认约束

| 维度 | 决策 |
|------|------|
| 源协议 | **仅 HTTP**（Pull + Webhook） |
| 接入范围 | **内部接口为主**，走内网/可信网络 |
| 部署 | 内部服务器，**单实例**，轻量数据摄取与落地 |
| 规模 | 先小规模（5–20 源） |
| 安全认证 | **第一阶段不做**（无 Admin 登录、无 Webhook 鉴权、无 OAuth2）；依赖网络隔离 |
| 映射 | JSONPath + 表达式；脚本逃生舱延后 |
| 拉取触发 | 定时调度（Cron / 固定间隔） |
| 增量（MVP） | **timestamp** 一种；其余策略 Phase 1.5 |
| 分页（MVP） | **page/page_size** 一种；其余策略 Phase 1.5 |
| Webhook（MVP） | 开放接入路径 + 审计日志，**无鉴权** |
| 业务 Sink | 接入**现有** PG（MVP 必做）；Kafka Phase 1.5 |
| PG 写入 | 默认 **upsert**，UI 可改为 insert / append_only |
| 扇出一致性 | MVP 仅单 Sink（PG）；多 Sink 扇出 Phase 1.5 |
| 技术栈 | **Java 21 + Spring Boot 3.x（最新稳定版）+ React** |
| 元数据库 | **内置嵌入式数据库**，不依赖外部 PG |
| 发布形态 | React 构建产物与 API **合并部署，同一端口** |
| 运维脚本 | 提供 **start / stop / status / restart** 自定义启停脚本 |

---

## 2. 技术选型

### 2.1 推荐方案：Java 模块化单体 + React 管理台

| 组件 | 选型 | 说明 |
|------|------|------|
| 语言 | Java 21 | LTS，虚拟线程可选 |
| 框架 | Spring Boot 3.x 最新稳定版 | 实现时取当前最新 3.x |
| 调度 | Quartz（JDBC JobStore） | Cron / 固定间隔；MVP 单实例运行 |
| HTTP 客户端 | Spring WebClient | 非阻塞，适合 I/O 密集拉取 |
| JSON / 路径 | Jackson + Jayway JsonPath | 映射与响应探测 |
| 表达式 | Aviator 或 Spring EL | 轻量字段计算 |
| 脚本逃生舱 | GraalJS（主）/ Groovy（备选） | **Phase 1.5**；沙箱 + 超时 |
| 元数据库 | **H2 File**（默认）或 SQLite | 单文件持久化，零外部依赖 |
| 业务 PG Sink | Spring JDBC + 外部连接 | 批量 upsert |
| Kafka Sink | Spring Kafka | **Phase 1.5** |
| 迁移 | Flyway | 元数据库 schema 版本管理 |
| 前端 | React 18 + TypeScript + Ant Design | 配置管理 UI（简单/高级模式） |
| 构建 | Maven 多模块 | 前端产物打入 boot jar |

### 2.2 元数据库：内置 H2（推荐）

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/http-ingestion-meta;MODE=PostgreSQL;AUTO_SERVER=FALSE
    driver-class-name: org.h2.Driver
  flyway:
    enabled: true
```

**选型理由**

- 不依赖外部 PG，单 jar / 单容器即可启动
- `MODE=PostgreSQL` 使 SQL 方言接近 PG，降低 Sink 侧与元数据侧心智差异
- Flyway + Quartz JDBC Store 与 H2 兼容良好
- 数据目录 `./data/` 挂载卷即可备份

> **注意**：H2 仅用于**配置、调度状态、运行日志、增量水位**；业务数据仍写入用户指定的外部 PG。

### 2.3 前后端合并部署（同一端口）

```
Maven 构建流水线
  http-ingestion-admin-ui: npm run build → dist/
  http-ingestion-boot: 将 dist/ 复制到 classpath:/static/
  最终 http-ingestion-service.jar 单产物启动 :8080

路由规则
  /api/**          → Spring MVC REST
  /ingress/**      → Webhook 接入
  /actuator/**     → 健康检查
  /*               → SPA 静态资源（index.html fallback）
```

**实现要点**

- `WebMvcConfigurer.addResourceHandlers` 托管 `classpath:/static/`
- `SpaFallbackController`：非 API 路径返回 `index.html`
- 开发期：UI `proxy → localhost:8080/api`；生产期：同源，无 CORS 问题

---

## 3. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│           Browser → http://host:8080 (同一端口)              │
│  /* SPA Admin UI    /api/* REST    /ingress/* Webhook       │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│     http-ingestion-service.jar (Spring Boot, 单实例)         │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Static/UI   │  │ REST API     │  │ Webhook Ingress  │  │
│  └─────────────┘  └──────────────┘  └──────────────────┘  │
│  ┌─────────────┐  ┌──────────────────────────────────────┐  │
│  │ Quartz      │──│ HTTP Connector Engine                 │  │
│  │ Scheduler   │  │ HTTP模板 / 分页 / 增量 / Transform   │  │
│  └─────────────┘  └──────────────────┬───────────────────┘  │
└────────────────────────────────────────┼──────────────────────┘
                                         │
              ┌──────────────────────────┼──────────────────┐
              ▼                          ▼                  ▼
     ┌────────────────┐      ┌──────────────┐   ┌──────────────┐
     │ H2 元数据库     │      │ 外部 PG Sink  │   │ 外部 Kafka   │
     │ (内置 file)     │      │ (MVP 必做)    │   │ (Phase 1.5)  │
     └────────────────┘      └──────────────┘   └──────────────┘
```

### 3.1 设计原则

1. **配置即数据**：HTTP 连接器全流程配置存 H2，版本化发布
2. **引擎插件化**：分页、总数来源、增量策略均为可插拔策略（MVP 先实现一种，接口预留扩展）
3. **Pull / Push 合流**：共用 Transform → Sink 管道
4. **单实例交付**：Scheduler + Engine + API 同 JVM、同进程；**第一阶段不考虑多实例 / Worker 拆分**

---

## 4. 连接器配置模型

### 4.1 基础信息与 HTTP 模板

```yaml
connector:
  id: "crm-customers"
  name: "CRM 客户列表"
  mode: pull                    # pull | push | both
  schedule:
    type: cron                  # cron | fixed_rate
    expression: "0 */5 * * * *"
  http:
    method: GET
    url: "http://internal-api.example.com/v1/customers"
    headers: {}                 # Phase 1：内部接口，无认证；可按需加固定 Header
    query:
      page_size: "${pagination.page_size}"
      updated_after: "${incremental.watermark}"
    timeout_ms: 30000
    retry:
      max_attempts: 3
      backoff: exponential
  # auth: Phase 1.5（bearer / api_key / basic / oauth2）
```

### 4.2 分页策略

| 策略 | MVP | 配置要点 | 终止条件 |
|------|-----|----------|----------|
| page/page_size | **是** | page 从 1、page_size | page > total_pages 或空页 |
| offset/limit | Phase 1.5 | offset 起始、limit 步长 | 空页或累计 ≥ total |
| cursor | Phase 1.5 | next_cursor JsonPath | next_cursor 为空 |
| link/header | Phase 1.5 | Link rel=next | 无 next |

**总数量来源**

```yaml
pagination:
  strategy: page_page_size
  total_count:
    source: same_response | separate_api | none
    json_path: "$.meta.total"              # same_response
    separate:                              # separate_api
      url: "http://internal-api.example.com/v1/customers/count"
      json_path: "$.count"
  stop_when:
    - empty_page
    - reached_total
    - max_pages: 1000
```

### 4.3 增量与条件

```yaml
incremental:
  enabled: true
  mode: timestamp               # MVP 仅 timestamp
  timestamp:
    response_path: "$.updated_at"
    request_param: "updated_after"
    overlap: "5m"
  # Phase 1.5: monotonic_id | cursor | rolling_window | composite
```

- **固定条件**：Query/Body 中常量
- **滚动刷新**：含 `${incremental.*}` 变量
- **自定义**：条件构建器（简单模式）或 JSON 高级编辑（高级模式）

### 4.4 全量 / 增量

```yaml
sync:
  type: incremental | full
  on_first_run: full
  full_reset:
    clear_state: false
    truncate_sink: false
```

**UI 操作**：立即全量 / 增量同步 / 重置水位后重跑 / **采样试运行**（limit N 条，可选不写 Sink 或写 staging 表）。

### 4.5 Webhook（MVP：无鉴权）

```yaml
webhook:
  enabled: true
  path: "/ingress/crm-customers"
  methods: [POST]
  # Phase 1.5: auth (api_key / signature / idempotency)
```

> MVP 依赖内网隔离；接入文档说明路径、payload 格式、成功响应契约（200 OK）。

### 4.6 配置版本

- draft → published；每次发布记 version
- 运行日志关联 connector_version
- MVP：发布即生效 + 历史版本只读；**回滚操作 Phase 1.5**

### 4.7 核心体验：试请求与字段探测

MVP 将「试请求」提升为**一等公民**，贯穿创建向导：

1. 粘贴或填写 URL → **发送试请求** → 展示原始 JSON 响应
2. 点选响应节点 → **自动生成 JsonPath**（如 `$.data[*].id`）
3. 映射预览：展示 Transform 后前 N 条记录
4. 分页/增量参数预览：展示将发出的实际 Query

**排错追踪**（运行失败后）：

```
源响应 → Transform 结果 → Sink 写入结果
         ↑ 标注失败步骤与记录索引
```

---

## 5. Transform 与 Sink

### 5.1 Transform 管道

```yaml
transform:
  input_root: "$.data"
  steps:
    - type: filter
      condition: "${record.status} == 'ACTIVE'"
    - type: map_fields
      mappings:
        - { target: customer_id, source: "$.id", type: long }
    - type: expression
      set:
        full_name: "name + ' (' + customer_id + ')'"
    # Phase 1.5: route, script
```

MVP Transform 步骤：**filter、map_fields、expression**。

### 5.2 PostgreSQL Sink（业务库）

```yaml
sinks:
  - id: pg_customers
    type: postgresql
    target:
      datasource_ref: "external-pg-1"
      schema: ingest
      table: crm_customers
    write_mode: upsert          # 默认；可选 insert | append_only
    keys: [customer_id]
    on_conflict: update         # update | ignore
    batch_size: 500
```

> MVP 说明：仅支持 upsert/insert 镜像，**不支持源端删除同步**（deletes）—— Phase 2 评估。

### 5.3 Kafka Sink（Phase 1.5）

```yaml
  - id: kafka_customers
    type: kafka
    target:
      bootstrap_servers_ref: "external-kafka"
      topic: ingest.crm.customers
      key: "${record.customer_id}"
    serialization: json
```

### 5.4 扇出一致性（Phase 1.5）

MVP 仅单 PG Sink。多 Sink 扇出引入后：

| 模式 | 行为 |
|------|------|
| best_effort（默认） | 各 Sink 独立写入，失败记日志，不阻塞其他 Sink |
| all_sinks_success | 全部 Sink 成功后方可推进增量水位 |

UI 选择 `all_sinks_success` 时须强提示不一致风险。

---

## 6. 管理台 UI

### 6.1 简单模式 / 高级模式

| 模式 | 面向 | 可见能力 |
|------|------|----------|
| **简单模式**（默认） | 数据工程师快速接入 | 向导创建、试请求、page 分页、timestamp 增量、map/filter、PG upsert、Cron |
| **高级模式** | 复杂场景 | JSON 高级编辑、expression、separate count API、fanout 策略（1.5 后） |

简单模式**隐藏**：composite 增量、脚本、路由、per_sink_error_policy。

### 6.2 内置模板（MVP 提供 2 个）

1. **REST 分页列表**：page/page_size + 全量/增量 timestamp → PG upsert
2. **Webhook JSON 数组**：`POST /ingress/{id}` + `input_root` 指向数组节点 → PG upsert

---

## 7. 元数据库表结构（H2）

| 表 | 用途 |
|----|------|
| `connector` | HTTP 数据源主表 |
| `connector_version` | 配置 JSON（draft/published） |
| `connector_schedule` | Cron / 间隔 |
| `connector_state` | 增量水位 |
| `job_run` | 运行摘要 |
| `job_run_detail` | 分页明细、错误样本、逐步追踪 |
| `webhook_event` | Push 审计 |
| `datasource_ref` | 外部 PG 连接引用（Kafka Phase 1.5） |
| `qrtz_*` | Quartz 调度表 |

> `secret_ref` 表 Phase 1.5（引入认证后）。Phase 1 连接信息通过环境变量 / `datasource_ref` 明文配置（内网可信）。

---

## 8. 运行时

### 8.1 Phase 1：单实例（唯一交付形态）

- 单 `http-ingestion-service.jar`，端口 8080
- H2 文件库路径 `./data/http-ingestion-meta`
- Quartz JDBC JobStore（基于 H2），**不做集群调度**
- **不考虑**多实例、负载均衡、Worker 拆分

### 8.2 错误处理

| 场景 | 策略 |
|------|------|
| HTTP 5xx / 超时 | 指数退避重试 |
| HTTP 4xx（非 429） | 标记失败，UI 可见 |
| 分页中途失败 | 不推进水位 |
| Transform 单条失败 | 默认跳过记样本；可配 fail_fast |
| Sink 失败 | 不推进水位 |

---

## 9. 安全（分阶段）

### 9.1 Phase 1：不做认证

| 层面 | 策略 |
|------|------|
| Admin UI / API | **无登录**，依赖内网访问控制 |
| Webhook `/ingress/**` | **无鉴权**，依赖网络隔离 |
| 源 HTTP 调用 | 内部接口，默认无 Token；可按需配置固定 Header |
| 密钥 | Phase 1 无 `secret_ref`；PG 连接串通过环境变量注入 |
| 日志 | 响应体采样入库时截断，避免过大 |

### 9.2 Phase 1.5+

- Admin：简单账号 → LDAP/SSO
- Webhook：API Key → HMAC 签名校验
- 源认证：OAuth2 / Bearer / API Key
- 脚本沙箱：超时、禁文件/网络 IO
- 审计：发布、触发、水位重置记操作人

---

## 10. 部署与运维

### 10.1 Docker Compose（可选）

```yaml
services:
  http-ingestion-service:
    image: http-ingestion-service:1.0
    ports: ["8080:8080"]
    environment:
      META_DB_PATH: /app/data
      EXTERNAL_PG_URL: ${EXISTING_PG}   # Sink 使用
    volumes:
      - ./data:/app/data
```

### 10.2 自定义启停脚本

交付物包含 `scripts/` 目录，支持 **jar 直启** 与 **Docker** 两种模式。

```
scripts/
├── start.sh / start.ps1       # 启动服务
├── stop.sh / stop.ps1         # 优雅停止（SIGTERM → 等待 Quartz 任务收尾 → 退出）
├── status.sh / status.ps1     # 检查进程 / 健康端点
├── restart.sh / restart.ps1   # stop + start
└── env.example                # 环境变量模板
```

**start 脚本行为**

1. 检查 `./data/` 目录，不存在则创建
2. 读取 `env` 或 `env.example` 中的 `JAVA_OPTS`、`SERVER_PORT`、`META_DB_PATH`、`EXTERNAL_PG_URL`
3. 检查端口占用与是否已有 PID 文件（`./data/http-ingestion.pid`）
4. 后台启动：`java $JAVA_OPTS -jar http-ingestion-service.jar`
5. 轮询 `/actuator/health` 直至 `UP` 或超时失败

**stop 脚本行为**

1. 读取 PID 文件，发送 `SIGTERM`
2. 等待最多 60s（可配置 `STOP_TIMEOUT`）
3. 超时则 `SIGKILL` 并清理 PID 文件

**status 脚本行为**

1. 检查 PID 文件与进程存活
2. 请求 `/actuator/health`，输出 JSON 摘要

> Windows 提供等价 `.ps1`；Linux/macOS 提供 `.sh`。脚本纳入 Maven 打包，随 release 产物分发。

### 10.3 健康检查

`/actuator/health` 探针项：

| 探针 | MVP |
|------|-----|
| H2 元数据库 | 必做 |
| 外部 PG Sink 连通性 | 必做（按已配置的 datasource_ref） |
| 磁盘空间 `./data` | 建议 |
| Kafka | Phase 1.5 |

### 10.4 备份与恢复

| 项 | 说明 |
|----|------|
| 备份对象 | `./data/http-ingestion-meta.*`（H2 文件） |
| 备份方式 | 停服后拷贝（脚本 `stop` → 拷贝 → `start`）；或文件级快照 |
| 恢复 | 停服 → 替换 H2 文件 → 启动 → 验证 Flyway 版本一致 |
| 文档 | `docs/ops/backup-restore.md`（MVP 交付） |

### 10.5 监控与告警（MVP 最小集）

**Prometheus 指标**（`/actuator/prometheus`，MVP 建议纳入）：

- `ingestion_job_total{status}` — 任务成功/失败计数
- `ingestion_job_duration_seconds` — 任务耗时
- `ingestion_records_processed_total` — 处理记录数
- `ingestion_watermark_lag_seconds` — 水位滞后（相对当前时间）

**告警规则**（可用外部 Prometheus/脚本轮询）：

| 告警 | 条件 |
|------|------|
| 连续失败 | 同一连接器连续 **3 次** job 失败 |
| 水位停滞 | 增量连接器 **N 小时**（默认 6h）水位未更新且调度正常 |
| 服务不可用 | `/actuator/health` 非 UP 超过 1 分钟 |

> MVP 不内置通知渠道（邮件/飞书）；告警通过指标 + 文档说明对接现有监控平台。

---

## 11. Maven 模块

```
http-ingestion-service-parent
├── http-ingestion-core          # HTTP / 分页 / 增量 / Transform 引擎
├── http-ingestion-sink-pg       # 外部 PG 写入
├── http-ingestion-sink-kafka    # Kafka 写入（Phase 1.5 实现）
├── http-ingestion-api           # REST + Webhook + SPA 静态资源
├── http-ingestion-scheduler     # Quartz 集成
├── http-ingestion-admin-ui      # React 源码（构建后复制至 api/static）
├── http-ingestion-boot          # 启动入口，聚合打包
└── scripts/                     # 启停脚本（打入 release 包）
```

**Maven 坐标示例**：`com.pcitech.http.ingestion:http-ingestion-service-parent:1.0.0-SNAPSHOT`

---

## 12. 交付阶段

### Phase 0：技术验证（1 周）

用 **1–2 个真实内部 API** 做端到端 spike：

- 拉取 → page 分页 → timestamp 增量 → map_fields → PG upsert
- 验证 JsonPath 命中、水位推进、H2 持久化
- 产出：spike 报告 + UI 字段清单（锁定向导步骤）

### Phase 1：MVP（6 周）

**P0 — 必须交付**

| # | 能力 |
|---|------|
| 1 | HTTP Connector CRUD、版本发布（发布即生效） |
| 2 | **试请求 + 字段探测 + 映射预览**（核心体验） |
| 3 | 分页：**page/page_size** |
| 4 | 增量：**timestamp** |
| 5 | Transform：**filter、map_fields、expression** |
| 6 | Sink：**外部 PG**（upsert / insert 可配） |
| 7 | 调度：Cron + 固定间隔；支持「立即跑一次」 |
| 8 | 运行历史、**逐步排错追踪**（页码 / 记录 / 步骤） |
| 9 | 水位管理：查看、重置 |
| 10 | **单 jar 部署** + 内置 H2 + 内嵌 Admin UI |
| 11 | **启停脚本**（start / stop / status / restart，sh + ps1） |
| 12 | 健康检查 + 备份恢复文档 + Prometheus 指标 |
| 13 | UI 简单模式 + 2 个内置模板 |
| 14 | 采样试运行（limit N 条） |

**P1 — MVP 后紧跟（Phase 1.5，约 +2 周）**

| # | 能力 |
|---|------|
| 1 | Webhook 接入（仍无鉴权）+ 审计 |
| 2 | Kafka Sink |
| 3 | 分页扩展：offset/limit、cursor |
| 4 | 增量扩展：monotonic_id、rolling_window |
| 5 | 多 Sink 扇出 + fanout 一致性配置 |
| 6 | 配置版本回滚 |
| 7 | 脚本步骤（GraalJS） |
| 8 | 源认证：Bearer / API Key / OAuth2 |
| 9 | Webhook 鉴权：API Key → 签名校验 |
| 10 | Admin 登录（简单账号） |

### Phase 2：平台化（远期）

- 多实例 / Worker 拆分 / 分布式调度
- 元库外置（外部 PG）
- Webhook 幂等去重
- Schema 演进（Avro）
- 源端删除同步
- GraphQL over HTTP 分页插件

---

## 13. 后期扩展预留

| 能力 | 方式 | 阶段 |
|------|------|------|
| Webhook 签名校验 | `webhook.signature` + Filter | 1.5 |
| 幂等去重 | `webhook.idempotency` + 去重表 | 2 |
| OAuth2 | `auth.oauth2` + token 缓存 | 1.5 |
| 多实例 / Worker | 拆进程 + 分布式锁 | 2 |
| 元库外置 | 配置切换至外部 PG | 2 |
| GraphQL over HTTP | 新分页协议插件 | 2 |
| 非 HTTP 协议 | 另立产品（如 `jdbc-ingestion-service`） | — |

---

## 14. 决策记录

| 决策 | 理由 |
|------|------|
| 产品名 `http-ingestion-service` | `{protocol}-{function}-service` 规范；诚实标注 HTTP-only |
| Java + Spring Boot 3.x | 调度、事务、Kafka、嵌入式脚本生态成熟 |
| 元库 H2 内置 | 零外部依赖，单 jar 极简部署 |
| UI 与 API 同端口 | 内部工具简化运维，无需额外 Nginx 反代 UI |
| 业务 Sink 用外部 PG | 与现有数据平台集成 |
| 默认 upsert | 符合多数落地场景 |
| **Phase 1 不做安全认证** | 内部接口 + 网络隔离，降低首期复杂度 |
| **Phase 1 单实例** | 5–20 源规模足够；避免过早引入分布式复杂度 |
| **MVP 收窄为 Pull → PG** | 6 周可交付可验证闭环；Kafka/Webhook 跟 1.5 |
| **试请求为一等公民** | 决定数据工程师首次接入体验与排错效率 |
| **交付启停脚本** | 运维主用户诉求；jar 直启与 Docker 均覆盖 |
| 脚本 / OAuth / 多实例延后 | 降低 MVP 风险，spike 验证后再扩展 |
