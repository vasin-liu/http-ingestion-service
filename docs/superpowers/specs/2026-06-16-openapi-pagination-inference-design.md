# OpenAPI pagination inference — design

**Date:** 2026-06-16  
**Status:** Implemented

## Goal

When importing a connector from OpenAPI, pre-fill pagination settings from operation parameters and response envelope fields instead of always using generic defaults.

## Inference rules

| Strategy | Trigger (query params) | Notes |
|----------|------------------------|-------|
| `offset_limit` | `offset`/`skip` + `limit` | `page_start: 0`, `page_value_type: offset` |
| `cursor` | `cursor`, `page_token`, `next_token`, `after` | Location `query` or `body`; paths from `meta` / `pagination` |
| `page_page_size` | `page` (+ optional `page_size`/`limit`/…) | Default `page_size_param` when only `page` present |

**Total count:** `$.total`, `$.meta.total`, or `$.pagination.total` when matching fields exist on the response envelope.

**No inference:** non-GET operations, or GET without pagination signals.

## API surface

- `OpenApiOperationDto.suggestedPagination` — JSON object matching connector `pagination` config; `null` when not inferred.
- UI merges with `defaultConfig.pagination` on wizard import and batch create.

## Out of scope

- `link_header` strategy (cannot be inferred from spec alone)
- Custom vendor extensions (`x-pagination-*`)
