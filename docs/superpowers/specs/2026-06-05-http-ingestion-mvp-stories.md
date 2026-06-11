# HTTP Ingestion Service — MVP 用户故事与验收标准

> 版本：v1  
> 日期：2026-06-05  
> 对应设计规格：`2026-06-05-http-ingestion-service-design.md`（v2）  
> 范围：Phase 1 MVP（6 周，Pull → PG 闭环）

---

## 角色定义

| 角色 | 缩写 | 说明 |
|------|------|------|
| 数据工程师 | DE | 主用户：配置连接器、排错、管理水位 |
| 运维 | OPS | 部署、启停、备份、监控 |
| 系统 | SYS | 服务自身行为（调度、引擎、持久化） |

---

## Epic 1：服务部署与运维

### US-1.1 一键启动服务

**作为** 运维，**我希望** 用一条命令启动服务，**以便** 无需手动拼 Java 参数。

**验收标准**

- [ ] 执行 `scripts/start.sh`（Linux/macOS）或 `scripts/start.ps1`（Windows）可后台启动服务
- [ ] 启动前自动创建 `./data/`（若不存在）
- [ ] 若 PID 文件存在且进程存活，拒绝重复启动并给出明确提示
- [ ] 启动后轮询 `http://localhost:${SERVER_PORT}/actuator/health`，状态为 `UP` 时输出成功；超时（默认 120s）则退出非零
- [ ] 支持通过 `scripts/env` 或环境变量配置 `SERVER_PORT`、`JAVA_OPTS`、`JAR_PATH`、`META_DB_PATH`

---

### US-1.2 优雅停止服务

**作为** 运维，**我希望** 用一条命令停止服务，**以便** 尽量让进行中的同步任务收尾。

**验收标准**

- [ ] 执行 `scripts/stop.sh` / `scripts/stop.ps1` 向进程发送 SIGTERM / 等价优雅停止信号
- [ ] 默认等待 60s（`STOP_TIMEOUT` 可配置）；超时后强制终止并清理 PID 文件
- [ ] 无 PID 文件或进程不存在时，输出提示并以退出码 0 结束（幂等）
- [ ] 停止后 `/actuator/health` 不可达或返回非 UP

---

### US-1.3 查看服务状态

**作为** 运维，**我希望** 快速确认服务是否在跑、健康与否，**以便** 排障或接入监控。

**验收标准**

- [ ] `scripts/status.sh` / `scripts/status.ps1` 输出：PID、进程存活、health JSON 摘要
- [ ] health 非 UP 时退出码非零
- [ ] 无进程时退出码非零并提示「未运行」

---

### US-1.4 重启服务

**作为** 运维，**我希望** 一条命令完成重启，**以便** 升级 jar 或应用配置变更。

**验收标准**

- [ ] `scripts/restart.sh` / `scripts/restart.ps1` 依次执行 stop → start
- [ ] 任一步失败则中止并返回非零退出码

---

### US-1.5 元数据库备份与恢复

**作为** 运维，**我希望** 有明确的备份恢复步骤，**以便** H2 损坏或误操作时可恢复。

**验收标准**

- [ ] 提供 `docs/ops/backup-restore.md`，包含：停服备份、恢复、Flyway 版本校验步骤
- [ ] 备份对象为 `./data/http-ingestion-meta.*`
- [ ] 文档含至少一次恢复演练检查清单

---

### US-1.6 健康检查与指标

**作为** 运维，**我希望** 通过标准端点检查依赖连通性，**以便** 接入现有监控。

**验收标准**

- [ ] `GET /actuator/health` 返回整体状态；包含 H2、外部 PG Sink 探针
- [ ] `GET /actuator/prometheus` 暴露指标：`ingestion_job_total`、`ingestion_job_duration_seconds`、`ingestion_records_processed_total`、`ingestion_watermark_lag_seconds`
- [ ] 健康检查失败时，响应体标明失败组件名称

---

## Epic 2：连接器创建与试请求（核心体验）

### US-2.1 创建 Pull 连接器（简单模式向导）

**作为** 数据工程师，**我希望** 通过向导创建 HTTP Pull 连接器，**以便** 不写代码即可接入内部 API。

**验收标准**

- [ ] 向导步骤：基本信息 → HTTP 请求 → 试请求 → 分页 → 增量 → 映射 → Sink → 调度 → 确认发布
- [ ] 必填项缺失时，步骤内联校验并阻止进入下一步
- [ ] 保存为 draft；发布后为 published 版本，发布即生效
- [ ] 简单模式不展示：composite 增量、脚本、路由、fanout 策略

---

### US-2.2 试请求与响应预览

**作为** 数据工程师，**我希望** 在保存前发送试请求并查看原始响应，**以便** 确认 URL 和参数正确。

**验收标准**

- [ ] UI 提供「试请求」按钮，展示 HTTP 状态码、耗时、响应体（JSON 格式化）
- [ ] 响应体过大时截断展示（如 > 1MB），并提示完整内容长度
- [ ] 请求失败（超时、4xx、5xx）时展示错误信息，不崩溃
- [ ] 试请求**不写入** Sink、**不推进**增量水位

---

### US-2.3 点选生成 JsonPath

**作为** 数据工程师，**我希望** 在响应树上点选字段自动生成 JsonPath，**以便** 减少手写路径错误。

**验收标准**

- [ ] 试请求成功后，响应以可展开树展示
- [ ] 点击节点生成 JsonPath 并填入当前编辑字段（如 `input_root`、映射 `source`）
- [ ] 数组节点生成带 `[*]` 的路径（如 `$.data[*].id`）
- [ ] 生成后展示「命中预览」：该路径在当前响应中的示例值

---

### US-2.4 映射预览

**作为** 数据工程师，**我希望** 发布前预览 Transform 后的记录，**以便** 确认字段映射正确。

**验收标准**

- [ ] 基于试请求响应，执行 filter + map_fields + expression，展示前 N 条（默认 10）结果
- [ ] 单条 Transform 失败时，标注失败记录索引与原因，不阻塞其余记录预览
- [ ] 预览**不写入** Sink

---

### US-2.5 使用内置模板创建

**作为** 数据工程师，**我希望** 从模板一键创建连接器，**以便** 常见场景快速起步。

**验收标准**

- [ ] 提供模板「REST 分页列表」：预填 page/page_size、timestamp 增量、PG upsert 骨架
- [ ] 提供模板「Webhook JSON 数组」：预填 `mode: push`、`input_root` 指向数组
- [ ] 从模板创建后进入向导，所有字段可编辑

---

## Epic 3：分页、增量与同步

### US-3.1 配置 page/page_size 分页

**作为** 数据工程师，**我希望** 配置页码分页并自动翻页拉取，**以便** 同步列表类内部 API。

**验收标准**

- [ ] 支持配置：page 参数名、page_size 参数名、起始页、page_size 值
- [ ] 终止条件：空页 **或** `page > total_pages`（total 来自 `same_response` JsonPath 或 `none`）
- [ ] 支持 `max_pages` 上限（默认 1000），达到后停止并标记 run 为部分完成
- [ ] `job_run_detail` 记录每页请求参数、返回条数、耗时

---

### US-3.2 配置 timestamp 增量

**作为** 数据工程师，**我希望** 按更新时间戳增量拉取，**以便** 避免每次全量。

**验收标准**

- [ ] 配置：`response_path`（从记录取时间戳）、`request_param`（写入 Query）、`overlap`（如 5m）
- [ ] 首次运行按 `on_first_run: full` 执行全量；后续按 watermark 增量
- [ ] 同步成功后推进 watermark 为本次批次最大时间戳（减 overlap）
- [ ] 分页中途失败**不推进** watermark

---

### US-3.3 手动触发同步

**作为** 数据工程师，**我希望** 立即触发全量或增量同步，**以便** 不等待 Cron。

**验收标准**

- [ ] 连接器详情页提供「立即全量同步」「立即增量同步」
- [ ] 触发后创建 `job_run`，状态流转：pending → running → success / failed
- [ ] 同一连接器并发触发时，后者排队或拒绝（行为须在 UI 说明，且一致）

---

### US-3.4 采样试运行

**作为** 数据工程师，**我希望** 限制处理条数做试跑，**以便** 验证配置而不污染生产表。

**验收标准**

- [ ] 支持「采样试运行」，可设 `limit`（如 10 条）
- [ ] 可选：不写 Sink **或** 写入 staging 表（若配置）
- [ ] 试跑记录记入 `job_run`，类型标记为 `sample`
- [ ] 采样试跑**不推进** watermark

---

### US-3.5 水位查看与重置

**作为** 数据工程师，**我希望** 查看并重置增量水位，**以便** 补数或重跑历史。

**验收标准**

- [ ] 连接器详情展示当前 watermark 值、最后成功更新时间
- [ ] 「重置水位」需二次确认；重置后下次增量按全量或指定策略执行（与 `on_first_run` 配置一致）
- [ ] 重置操作记入审计（操作时间；Phase 1 无登录则记 system/local）

---

### US-3.6 Cron 与固定间隔调度

**作为** 数据工程师，**我希望** 配置 Cron 或固定间隔自动拉取，**以便** 持续同步。

**验收标准**

- [ ] 支持 `cron` 表达式与 `fixed_rate`（秒）
- [ ] 发布后的调度在 Quartz 中注册；暂停/恢复调度可在 UI 操作
- [ ] 修改调度后，已 running 的任务不被强杀

---

## Epic 4：Transform 与 PG Sink

### US-4.1 字段映射与过滤

**作为** 数据工程师，**我希望** 配置 filter 和 map_fields，**以便** 只写入需要的字段。

**验收标准**

- [ ] `filter`：单条条件表达式，不匹配则跳过
- [ ] `map_fields`：支持 source JsonPath、target 列名、类型（string/long/double/boolean/datetime）
- [ ] 类型转换失败记入 `job_run_detail` 样本，默认跳过该条

---

### US-4.2 表达式计算字段

**作为** 数据工程师，**我希望** 用表达式生成派生字段，**以便** 不做脚本也能拼接/计算。

**验收标准**

- [ ] `expression` 步骤可对已有字段做字符串拼接、简单运算
- [ ] 表达式错误记入错误样本，默认跳过该条
- [ ] 高级模式可编辑表达式；简单模式提供常用模板（拼接、默认值）

---

### US-4.3 写入外部 PostgreSQL（upsert）

**作为** 数据工程师，**我希望** 数据 upsert 到现有 PG 表，**以便** 下游直接消费。

**验收标准**

- [ ] 配置：`datasource_ref`、schema、table、`keys`、`write_mode`（upsert/insert）
- [ ] 默认 `upsert` + `on_conflict: update`
- [ ] 批量写入，`batch_size` 默认 500
- [ ] Sink 失败时 job 标记 failed，**不推进** watermark
- [ ] `job_run` 记录写入成功条数、失败条数

---

## Epic 5：运行历史与排错

### US-5.1 查看运行历史列表

**作为** 数据工程师，**我希望** 查看连接器历次运行摘要，**以便** 掌握同步情况。

**验收标准**

- [ ] 列表字段：开始/结束时间、类型（full/incremental/sample）、状态、处理条数、失败条数、耗时
- [ ] 支持按时间倒序；默认展示最近 50 条，可分页
- [ ] 失败记录以醒目状态标识

---

### US-5.2 逐步排错追踪

**作为** 数据工程师，**我希望** 点开失败任务看到失败位置，**以便** 无需查服务端日志。

**验收标准**

- [ ] 失败详情展示：失败阶段（http / transform / sink）、页码（若适用）、记录索引、错误消息
- [ ] 展示该条记录：源数据摘要 → Transform 后 → Sink 结果（若有）
- [ ] 至少保留最近 20 条错误样本 per job_run
- [ ] HTTP 阶段失败展示：请求 URL、状态码、响应片段

---

### US-5.3 告警指标可被采集

**作为** 运维，**我希望** 外部监控能发现连续失败与水位停滞，**以便** 及时介入。

**验收标准**

- [ ] 文档定义告警规则：连续 3 次失败、水位 6h 未更新（可配置）
- [ ] 对应指标可从 Prometheus 拉取
- [ ] 提供 PromQL 示例（文档或 `docs/ops/monitoring.md`）

---

## Epic 6：配置版本（MVP 精简）

### US-6.1 发布配置版本

**作为** 数据工程师，**我希望** 草稿发布后生效，**以便** 调度使用稳定配置。

**验收标准**

- [ ] draft 可多次保存；publish 生成递增 version
- [ ] 发布后 Quartz 使用最新 published 版本
- [ ] 历史版本只读可查；**MVP 不支持一键回滚**（Phase 1.5）

---

## 非功能验收（横切）

| 编号 | 标准 |
|------|------|
| NF-1 | 单 jar `java -jar` 或启停脚本启动，无需外部元数据库 |
| NF-2 | UI 与 API 同源 `:8080`，刷新深层路由不 404 |
| NF-3 | Phase 1 无 Admin 登录、Webhook 无鉴权（内网） |
| NF-4 | 单实例：无双活、无 Worker 拆分 |
| NF-5 | 新源接入（熟悉 API 的 DE）端到端 < 2 小时（试点验证） |
| NF-6 | 单连接器日同步成功率 > 99%（排除源站故障，试点验证） |

---

## MVP 完成定义（Definition of Done）

满足以下全部条件，视为 Phase 1 MVP 完成：

1. 上述 **P0 用户故事**验收标准全部通过（Epic 1–5 中标记为 MVP 的条目）
2. Phase 0 spike 报告已归档，且 spike 中使用的 API 在 MVP 中可复现
3. `docs/ops/backup-restore.md` 与监控文档已交付并完成一次备份恢复演练
4. 至少 **2 个真实内部源**完成 Pull → PG 生产同步，连续运行 7 天无人工干预（除源站维护）
5. 启停脚本在目标环境（Linux 或 Windows 至少各一种）实测通过

---

## 故事优先级与迭代建议

### Sprint 1（周 1–2）：能跑起来

- US-1.1 ~ US-1.4、US-1.6
- US-2.1、US-2.2（试请求 API + 最简 UI）
- US-6.1（draft/publish 数据模型）

### Sprint 2（周 3–4）：能同步

- US-2.3、US-2.4、US-3.1、US-3.2
- US-4.1、US-4.2、US-4.3
- US-3.3、US-3.6

### Sprint 3（周 5–6）：能排错、能运维

- US-3.4、US-3.5
- US-5.1、US-5.2、US-5.3
- US-1.5、US-2.5
- NF 全量验收 + 试点 7 天

---

## 不在 MVP 范围（明确排除）

- Admin 登录 / LDAP / SSO
- Webhook 鉴权、签名校验、幂等
- OAuth2 / Bearer 源认证
- Kafka Sink、多 Sink 扇出
- offset/cursor/link 分页；monotonic_id/composite 增量
- 配置版本一键回滚
- 脚本步骤（GraalJS）
- 多实例 / Worker 拆分
- 源端删除同步
