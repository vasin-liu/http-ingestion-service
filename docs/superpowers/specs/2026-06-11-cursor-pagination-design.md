# Cursor 分页策略 — 设计规格

> 日期：2026-06-11  
> 状态：**已评审（brainstorming 确认）**  
> 基线：`2026-06-05-http-ingestion-service-design.md` §4.2 分页策略  
> 迭代主轴：能力扩展 → 分页与增量（A）→ Cursor/token 分页

---

## 1. 背景与目标

### 1.1 现状

| 能力 | 状态 |
|------|------|
| `page_page_size` 分页 | ✅ |
| `offset_limit` 分页 | ✅ |
| `cursor` 分页 | ❌ |
| timestamp 增量 | ✅（与分页正交） |
| Link/Header 分页 | ❌（后续） |
| monotonic_id / rolling_window 增量 | ❌（后续） |

`HttpPullEngine` 以递增 `page` 驱动翻页；`RequestBodyComposer` 支持 body 内 page/offset 注入。`ConnectorState.watermarkJson` 仅存 timestamp 增量水位，不持久化翻页 cursor。

### 1.2 目标

交付 **cursor/token 分页**策略，使数据工程师可通过向导或 JSON 配置接入「响应返回 nextCursor / pageToken」类 REST API。

**成功标准**

- [ ] `strategy: cursor` 在 Query 与 Body 两种 `location` 下均可全量拉取多页数据并写入 Sink
- [ ] 终止条件 `empty_cursor`、`has_more_false`、`empty_page` 可独立或组合配置（OR 语义）
- [ ] 与 timestamp 增量可并存（cursor 管单次 job 内翻页，timestamp 管 job 间水位）
- [ ] Java E2E（WireMock）+ Playwright 冒烟 + 单元测试全绿
- [ ] `docs/testing/test-matrix.md` 已更新

---

## 2. 已确认决策

| # | 决策 | 说明 |
|---|------|------|
| 1 | **实现路径** | 方案 1：扩展 `HttpPullEngine` + `PaginationSettings`；cursor 循环逻辑可抽 package-private helper，不做全量 Strategy 重构 |
| 2 | **传参位置** | Query + Body 均支持（`pagination.location`） |
| 3 | **终止语义** | `stop_when` 多选，**任一条件满足即停止**（OR） |
| 4 | **cursor 持久化** | 不写入 `ConnectorState`；仅单次 sync 内有效 |
| 5 | **OpenAPI 推断** | **本迭代不做**；后续迭代评估 |
| 6 | **示例模板** | 新增 `rest-cursor`（第 5 个 example 模板） |

---

## 3. 配置模型

### 3.1 完整示例（Query）

```yaml
pagination:
  strategy: cursor
  location: query
  cursor_param: cursor
  page_size_param: limit
  page_size: 100
  cursor_response_path: "$.meta.nextCursor"
  has_more_path: "$.meta.hasMore"
  first_page_omit_cursor: true
  stop_when:
    - empty_cursor
    - has_more_false
    - empty_page
  max_pages: 1000
```

### 3.2 完整示例（Body POST）

```yaml
http:
  method: POST
  url: "https://api.example.com/items/search"
  body_type: json
  body_json: |
    {"filter": {"status": "active"}}
pagination:
  strategy: cursor
  location: body
  cursor_param: pageToken
  page_size_param: pageSize
  page_size: 50
  cursor_response_path: "$.pagination.nextToken"
  first_page_omit_cursor: true
  stop_when:
    - empty_cursor
    - empty_page
  max_pages: 500
```

### 3.3 字段说明

| 字段 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `strategy` | 是 | — | 固定 `cursor` |
| `location` | 是 | `query` | `query` \| `body` |
| `cursor_param` | 是 | — | 请求中 cursor 参数/字段名 |
| `cursor_response_path` | 是 | — | 响应中下一页 cursor 的 JsonPath |
| `has_more_path` | 否 | null | 响应中布尔标志 JsonPath |
| `page_size_param` | 否 | null | 每页条数参数名；无则省略 |
| `page_size` | 否 | 100 | 每页条数值 |
| `first_page_omit_cursor` | 否 | `true` | `true` 首页不传 cursor；`false` 传空串 |
| `stop_when` | 否 | `[empty_cursor, empty_page]` | 见 §3.4 |
| `max_pages` | 否 | 1000 | 安全上限，防止死循环 |

**与 page 策略字段的关系**：`page_param`、`page_start`、`total_count` 在 `strategy: cursor` 时忽略。

### 3.4 终止条件（OR 语义）

| 值 | 触发条件 |
|----|----------|
| `empty_cursor` | `cursor_response_path` 解析为 null、缺失或空白字符串 |
| `has_more_false` | 配置了 `has_more_path` 且值为布尔 `false` |
| `empty_page` | 当前页 `transform.input_root` 下 records 为空 |

循环在**任一**已配置的 `stop_when` 条件满足时结束。若 `has_more_path` 未配置，则 `has_more_false` 项永不触发。

### 3.5 与增量并存

```yaml
incremental:
  enabled: true
  timestamp:
    response_path: "$.updated_at"
    request_param: updated_after
    overlap: 5m
```

- 每次 sync：cursor 从 null 翻至末页；job 结束后按 records 更新 timestamp 水位。
- 增量参数在**每一页**请求中注入（与现 page 策略一致）。

---

## 4. 架构与组件

### 4.1 改动文件

| 模块 | 文件 | 变更 |
|------|------|------|
| core | `RuntimeConnectorConfig.PaginationSettings` | 新增 cursor 字段 |
| core | `RuntimeConfigParser` | 解析 cursor 配置 |
| core | `HttpPullEngine` | cursor 翻页主循环 |
| core | `RequestBodyComposer` | body cursor 注入 |
| core | `ConnectorTemplateService` | `rest-cursor` 模板 |
| api | `MockE2eRestSourceController` | `/mock/e2e/cursor-items` |
| admin-ui | `ConnectorWizardPage` | 策略切换 + cursor 表单项 |
| admin-ui | `client.ts` / `defaultConfig` | 默认 cursor 字段 |
| admin-ui | i18n | 中英文文案 |
| boot | `HttpIngestionE2ETest` | `CursorPull` nested |
| boot | `ConnectorConfigFactory` | cursor 配置工厂 |
| admin-ui/e2e | `cursor-pagination.spec.ts`（新） | UI 冒烟 |
| docs | `test-matrix.md` | 矩阵更新 |

### 4.2 引擎流程

```
cursor := null
pageIndex := 0
loop while pageIndex < max_pages:
  build query/body with cursor (if not first_page_omit) + page_size + incremental
  execute HTTP
  parse records from input_root
  if empty_page in stop_when and records empty → break
  append records; update timestamp watermark from records
  nextCursor := read(cursor_response_path)
  if empty_cursor in stop_when and nextCursor blank → break
  if has_more_false in stop_when and hasMore == false → break
  cursor := nextCursor
  pageIndex++
```

失败抛出 `PullException(pageIndex, message)`，与现有行为一致。

### 4.3 `PaginationSettings` 扩展（Java record）

新增字段（nullable 用 Optional 或 null）：

```java
String cursorParam;
String cursorResponsePath;
String hasMorePath;
boolean firstPageOmitCursor;
List<String> stopWhen;
```

`RuntimeConfigParser` 在 `strategy` 非 `cursor` 时，cursor 字段为 null / 默认值。

### 4.4 UI 行为

向导步骤「分页 / 增量」：

1. `pagination.strategy` 下拉：`page_page_size` | `offset_limit` | `cursor`
2. 选 `cursor` 时：
   - 显示：location、cursor_param、cursor_response_path、has_more_path、first_page_omit_cursor、stop_when（Checkbox.Group）、page_size_param、page_size
   - 隐藏：page_param、page_start、total_count 相关项
3. 增量区块不变

---

## 5. Mock 与示例

### 5.1 Mock 端点（e2e profile）

**GET `/mock/e2e/cursor-items`**

| Query | 响应 |
|-------|------|
| 无 cursor | `{ "data": [{"id":1},{"id":2}], "meta": { "nextCursor": "page2", "hasMore": true } }` |
| `cursor=page2` | `{ "data": [{"id":3}], "meta": { "nextCursor": "", "hasMore": false } }` |

**POST `/mock/e2e/cursor-items`**（Body 模式 Playwright 可选用）

请求体含 `pageToken`；响应 `{ "data": [...], "pagination": { "nextToken": "..." } }`。

### 5.2 模板 `rest-cursor`

| 属性 | 值 |
|------|-----|
| ID | `rest-cursor` |
| 名称 | REST Cursor 分页示例 |
| 模式 | pull |
| 默认 URL | `https://api.example.com/items` |
| 分页 | cursor + query + stop_when 全三件套 |

---

## 6. 测试计划

| 层级 | 类 / Spec | 场景 |
|------|-----------|------|
| 单元 | `RequestBodyComposerTest` | body 首页 omit / 次页注入 cursor |
| 单元 | `CursorPaginationTest`（新，core） | stop_when 三种组合 + max_pages |
| Java E2E | `HttpIngestionE2ETest.CursorPull` | WireMock GET cursor 2 页 3 行 → PG |
| Java E2E | 同上 nested | WireMock POST body cursor + hasMore=false |
| Playwright | `cursor-pagination.spec.ts` | rest-cursor 模板 → Mock → 全量 sync |
| 回归 | 现有 offset/page E2E | 无破坏 |

---

## 7. 错误处理

| 场景 | 行为 |
|------|------|
| `cursor_response_path` 无效 | 视为 empty_cursor（若配置在 stop_when 中） |
| `has_more_path` 非布尔 | 忽略 has_more_false 判断，记录 debug 日志 |
| 超过 `max_pages` | 正常结束，job success（与 page 策略一致） |
| HTTP 4xx/5xx | `PullException`，job failed |

---

## 8. 范围外

- Link/Header 分页（`rel=next`）
- monotonic_id、rolling_window 增量
- OpenAPI 自动推断 cursor 参数
- 跨 job 持久化 cursor（中断续跑）
- cursor 作为增量水位类型

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| API cursor 编码/转义 | 原样传递字符串，不做 URL 二次编码（与现有 query 行为一致） |
| stop_when 配置不当导致多拉/少拉 | 向导默认 `[empty_cursor, empty_page]`；文档举例 |
| Engine 分支膨胀 | cursor 循环抽 `CursorPaginationLoop` helper |

---

## 10. 评审检查清单

- [x] 与用户确认：方案 1、Query+Body、stop_when OR、OpenAPI 延后
- [x] 配置字段完整，无 TBD
- [x] 与 timestamp 增量并存语义明确
- [x] 测试矩阵覆盖 Java E2E + Playwright
- [x] 范围边界清晰

---

## 11. 下一步

1. 用户审阅本 spec
2. 使用 **writing-plans** 生成 implementation plan
3. 按 AGENTS.md 同步交付单元 / Java E2E / Playwright 测试
