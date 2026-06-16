# Link/Header 分页策略 — 设计规格

> 日期：2026-06-11  
> 状态：**已确认（试点 Go 后下一迭代）**  
> 基线：`2026-06-05-http-ingestion-service-design.md` §4.2  
> 主轴：能力扩展 → 分页与增量 → **Link/Header 分页**

## 目标

支持 `pagination.strategy: link_header`，按响应头 `Link: <url>; rel="next"`（RFC 5988）翻页，直至 `no_next_link` 或 `empty_page`。

## 决策

| # | 决策 |
|---|------|
| 1 | 扩展 `HttpPullEngine` + `PaginationSettings`；逻辑抽 `LinkHeaderSupport` |
| 2 | `TrialResponseDto` 增加 `responseHeaders`（引擎读 Link；试请求 UI 可后续展示） |
| 3 | 首请求用配置 URL + query/增量；后续请求 **仅** 跟随 Link 绝对 URL（不再合并 query） |
| 4 | `stop_when` OR：`no_next_link`、`empty_page` |
| 5 | 示例模板 `rest-link-header`（第 6 个）；Mock `GET /mock/e2e/link-items` |

## 配置示例

```yaml
pagination:
  strategy: link_header
  link_header_name: Link
  link_rel: next
  stop_when: [no_next_link, empty_page]
  max_pages: 1000
```

## 范围外

OpenAPI 推断、跨 job 持久化 next URL、自定义 Header 名以外的多 rel 链式策略。
