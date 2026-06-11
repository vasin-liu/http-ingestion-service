# HTTP Ingestion Service — Sprint 1 任务拆解

> Sprint：1 / 3  
> 周期：第 1–2 周  
> 目标：**服务能跑起来 + 连接器可创建/试请求 + 配置可发布**  
> 对应故事：US-1.1 ~ US-1.4、US-1.6、US-2.1、US-2.2、US-6.1

---

## Sprint 1 完成标准（Sprint Goal）

- [ ] `mvn package` 产出可启动 jar，`scripts/start` 后 health=UP
- [ ] H2 + Flyway 基线表就绪（connector、connector_version、job_run 等）
- [ ] REST API 支持连接器 draft CRUD、publish
- [ ] UI 向导可创建连接器并完成试请求预览
- [ ] Phase 0 spike 报告归档（可与 Sprint 1 第 1 天并行）

---

## 任务总览

| ID | 任务 | 负责人 | 估时 | 依赖 | 状态 |
|----|------|--------|------|------|------|
| S1-01 | Maven 多模块骨架 | BE | 1d | — | Todo |
| S1-02 | H2 + Flyway 元库基线 | BE | 1d | S1-01 | Todo |
| S1-03 | Boot 启动 + 静态资源托管 | BE | 1d | S1-01 | Todo |
| S1-04 | Actuator health 探针 | BE | 0.5d | S1-03 | Todo |
| S1-05 | 启停脚本联调 | OPS/BE | 0.5d | S1-03 | Todo |
| S1-06 | connector 领域模型 + Repository | BE | 1d | S1-02 | Todo |
| S1-07 | Connector CRUD API（draft） | BE | 1d | S1-06 | Todo |
| S1-08 | Publish API + 版本递增 | BE | 0.5d | S1-07 | Todo |
| S1-09 | 试请求 API（TrialRequest） | BE | 1.5d | S1-07 | Todo |
| S1-10 | React 项目骨架 + 路由 | FE | 1d | S1-03 | Todo |
| S1-11 | 连接器列表 + 创建向导框架 | FE | 1.5d | S1-10 | Todo |
| S1-12 | 试请求 UI（响应 JSON 预览） | FE | 1d | S1-09, S1-11 | Todo |
| S1-13 | Phase 0 spike（真实 API） | BE | 1d | S1-09 | Todo |
| S1-14 | Sprint 1 集成测试 + 演示 | ALL | 1d | 全部 | Todo |

**合计**：约 10 人日（BE 6.5 + FE 3.5 + 联调 1）

---

## 详细任务说明

### S1-01 Maven 多模块骨架

**产出**

```
http-ingestion-service-parent/
├── http-ingestion-core
├── http-ingestion-sink-pg
├── http-ingestion-api
├── http-ingestion-scheduler
├── http-ingestion-admin-ui
├── http-ingestion-boot
└── scripts/（已有）
```

**验收**

- [ ] `mvn -q validate` 通过
- [ ] Java 21、Spring Boot 3.x parent 统一
- [ ] boot 模块依赖 api + scheduler + sink-pg

---

### S1-02 H2 + Flyway 元库基线

**产出**

- `V1__baseline.sql`：connector、connector_version、connector_schedule、connector_state、job_run、job_run_detail、datasource_ref、qrtz_* 占位或完整 Quartz 表

**验收**

- [ ] 空库启动自动迁移
- [ ] `jdbc:h2:file:./data/http-ingestion-meta;MODE=PostgreSQL`
- [ ] connector_version 存 JSON 配置字段（CLOB/JSON）

---

### S1-03 Boot 启动 + 静态资源托管

**产出**

- `SpaFallbackController`、`/api/**` 路由
- admin-ui build 复制至 `classpath:/static/`（Maven frontend 插件或 npm 脚本）

**验收**

- [ ] `java -jar` 启动 :8080
- [ ] 访问 `/` 返回 SPA index.html
- [ ] 刷新 `/connectors/new` 不 404

---

### S1-04 Actuator health 探针

**产出**

- H2 health indicator
- 外部 PG 连通性探针（读取 `EXTERNAL_PG_URL`，可选未配置时 UNKNOWN）

**验收**

- [ ] `GET /actuator/health` 返回 UP（PG 未配时不阻塞整体 UP，组件级标 UNKNOWN）
- [ ] 符合 US-1.6

---

### S1-05 启停脚本联调

**产出**

- 在目标环境验证 `scripts/start|stop|status|restart`
- 修正 `env.example` 默认值（若需）

**验收**

- [ ] start 后 health=UP；stop 后进程退出、PID 清理
- [ ] 日志写入 `./data/http-ingestion.log`

---

### S1-06 connector 领域模型 + Repository

**产出**

- `Connector`、`ConnectorVersion` 实体
- 状态：draft / published
- Spring Data JDBC 或 JPA（与项目约定一致）

**验收**

- [ ] 单元测试：保存 draft、查询 by id
- [ ] version 与 connector 1:N 关联

---

### S1-07 Connector CRUD API（draft）

**端点**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/connectors` | 列表 |
| GET | `/api/connectors/{id}` | 详情（含 draft config） |
| POST | `/api/connectors` | 创建 |
| PUT | `/api/connectors/{id}` | 更新 draft |
| DELETE | `/api/connectors/{id}` | 删除 |

**验收**

- [ ] OpenAPI 或 README 记录请求体结构（对齐设计规格 §4.1 子集）
- [ ] 校验 id、name 必填

---

### S1-08 Publish API + 版本递增

**端点**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/connectors/{id}/publish` | draft → published，version++ |

**验收**

- [ ] 发布后 draft 与 published 可区分查询
- [ ] 运行日志尚未实现时，表结构预留 `connector_version_id`

---

### S1-09 试请求 API（TrialRequest）

**端点**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/connectors/{id}/trial` | 或 `/api/trial-requests` |
| POST | `/api/trial-requests` | 无需保存连接器亦可试请求（推荐） |

**请求体**：method、url、headers、query、body、timeout_ms

**响应**：status_code、duration_ms、body（JSON）、error（若有）

**验收**

- [ ] 不写 Sink、不推进水位
- [ ] 超时与 4xx/5xx 返回结构化错误
- [ ] 响应 > 1MB 时截断并返回 `truncated: true`
- [ ] 符合 US-2.2

---

### S1-10 React 项目骨架 + 路由

**产出**

- React 18 + TS + Ant Design
- 路由：`/`、`/connectors`、`/connectors/new`、`/connectors/:id`

**验收**

- [ ] dev proxy `/api` → 8080
- [ ] 生产构建打入 boot jar

---

### S1-11 连接器列表 + 创建向导框架

**产出**

- 列表页：名称、mode、状态、最近更新
- 向导 Step 1–2：基本信息、HTTP 请求配置
- 简单模式布局（无高级 JSON 编辑器）

**验收**

- [ ] 可创建 draft 并回到列表
- [ ] 符合 US-2.1 前两步

---

### S1-12 试请求 UI（响应 JSON 预览）

**产出**

- 「试请求」按钮
- JSON 树形展示（可用现成组件）
- 错误态展示

**验收**

- [ ] 点击试请求后展示状态码、耗时、响应体
- [ ] 符合 US-2.2 全部验收项

---

### S1-13 Phase 0 spike（真实 API）

**产出**

- `docs/spike/phase0-report.md`
- 记录：真实内部 API URL、响应样例、建议 JsonPath、风险项

**验收**

- [ ] 用 TrialRequest API 成功拉取真实响应
- [ ] 报告含 UI 字段清单建议（供 Sprint 2）

---

### S1-14 Sprint 1 集成测试 + 演示

**产出**

- 演示脚本：启动 → 创建连接器 → 试请求 → 发布
- 已知问题清单

**验收**

- [ ] 团队演示通过 Sprint Goal 检查项
- [ ] 无 P0 阻塞缺陷

---

## 按角色分工

### 后端（BE）

```
S1-01 → S1-02 → S1-06 → S1-07 → S1-08 → S1-09 → S1-13
         ↘ S1-03 → S1-04 → S1-05
```

### 前端（FE）

```
S1-10 → S1-11 → S1-12
         ↑ 等待 S1-09 接口定义（第 3 天可 mock）
```

### 并行建议

| 天 | BE | FE |
|----|----|----|
| D1 | S1-01, S1-02 | S1-10 |
| D2 | S1-03, S1-04, S1-06 | S1-11（mock API） |
| D3 | S1-07, S1-09 接口定义 | S1-11 联调 |
| D4 | S1-08, S1-09 完成 | S1-12 |
| D5 | S1-05, S1-13 spike | S1-12  polish |
| D6–7 | S1-14 联调、修缺陷 | S1-14 |

---

## 风险与依赖

| 风险 | 缓解 |
|------|------|
| 真实内部 API 第 1 周不可用 | 用 WireMock 或公开 JSON API 代替 spike |
| 前后端接口未对齐 | D2 输出 OpenAPI / 共享 TypeScript 类型 |
| Windows 启停脚本未测 | D5 OPS 在 Windows 实测 `*.ps1` |
| Quartz 表与 H2 兼容性 | S1-02 优先验证 Flyway + Quartz 最小配置 |

---

## 不在 Sprint 1 范围

- 分页/增量引擎执行（Sprint 2）
- PG Sink 写入（Sprint 2）
- JsonPath 点选生成（Sprint 2）
- 调度 Cron 注册（Sprint 2）
- Prometheus 业务指标（Sprint 3，health 本期做）

---

## 粘贴用：项目管理工具格式

### Jira / 禅道 史诗

```
Epic: [Sprint1] 服务启动与连接器试请求
```

### 子任务标题（可直接导入）

```
[S1-01] Maven 多模块骨架
[S1-02] H2 + Flyway 元库基线
[S1-03] Boot 启动与 SPA 静态资源托管
[S1-04] Actuator health 探针（H2 + PG）
[S1-05] 启停脚本目标环境联调
[S1-06] Connector 领域模型与 Repository
[S1-07] Connector CRUD API（draft）
[S1-08] Publish API 与版本递增
[S1-09] 试请求 TrialRequest API
[S1-10] React 骨架与路由
[S1-11] 连接器列表与创建向导（Step1-2）
[S1-12] 试请求 UI 与 JSON 预览
[S1-13] Phase 0 真实 API Spike 报告
[S1-14] Sprint1 集成测试与演示
```

### 看板列建议

`Backlog` → `In Progress` → `Code Review` → `QA` → `Done`
