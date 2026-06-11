# HTTP Ingestion Service — 备份与恢复指南

> 版本：v1  
> 日期：2026-06-05  
> 适用：Phase 1 MVP（单实例 + 内置 H2 元数据库）

---

## 1. 概述

HTTP Ingestion Service 将**配置、调度状态、运行日志、增量水位**存储在本地 H2 文件库中。业务数据写入外部 PostgreSQL，**不在本备份范围内**。

| 数据类型 | 存储位置 | 是否本指南覆盖 |
|----------|----------|----------------|
| 连接器配置、版本 | H2 `./data/http-ingestion-meta.*` | ✅ |
| 增量水位 | H2 | ✅ |
| 运行历史 / 排错样本 | H2 | ✅ |
| Quartz 调度状态 | H2 | ✅ |
| 业务表数据 | 外部 PG | ❌（由 DBA / PG 备份策略负责） |

---

## 2. 备份对象

默认数据目录（可通过 `META_DB_PATH` 配置）：

```
./data/
├── http-ingestion-meta.mv.db      # H2 主数据文件（必有）
├── http-ingestion-meta.trace.db   # H2 跟踪文件（可能存在）
├── http-ingestion.pid             # 运行时 PID（无需备份）
└── http-ingestion.log             # 应用日志（可选备份）
```

**必须备份**：`http-ingestion-meta.mv.db`（及同前缀的 `.trace.db` 若存在）。

---

## 3. 备份策略

### 3.1 推荐方式：停服后冷备份

最可靠，避免 H2 文件拷贝不一致。

```bash
# Linux/macOS
./scripts/stop.sh
tar -czf "backup/http-ingestion-meta-$(date +%Y%m%d-%H%M%S).tar.gz" -C ./data http-ingestion-meta.mv.db http-ingestion-meta.trace.db 2>/dev/null || \
tar -czf "backup/http-ingestion-meta-$(date +%Y%m%d-%H%M%S).tar.gz" -C ./data http-ingestion-meta.mv.db
./scripts/start.sh
```

```powershell
# Windows
.\scripts\stop.ps1
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = "backup\http-ingestion-meta-$stamp.zip"
Compress-Archive -Path "data\http-ingestion-meta.*" -DestinationPath $dest -Force
.\scripts\start.ps1
```

### 3.2 备份频率建议

| 环境 | 频率 | 保留 |
|------|------|------|
| 生产 | 每日 1 次（低峰停服或维护窗口） | 最近 14 天 |
| 预发 | 每周 1 次 | 最近 4 份 |
| 开发 | 重大配置变更前手动备份 | 按需 |

### 3.3 可选：不停机备份（风险较高）

H2 在服务运行时直接拷贝文件**可能**得到不一致快照。仅在无法停服时使用，且恢复后须做完整性校验。

若必须热备份：

1. 暂停所有连接器调度（UI 或 API）
2. 等待当前 `job_run` 全部结束
3. 立即拷贝 `./data/http-ingestion-meta.mv.db`
4. 恢复调度

> MVP 未提供 H2 在线备份 API；生产环境优先使用 **3.1 冷备份**。

---

## 4. 恢复流程

### 4.1 前置检查

- [ ] 确认备份包完整（可解压 / 文件大小合理）
- [ ] 确认目标环境与备份时 **Flyway schema 版本兼容**（同大版本 jar）
- [ ] 通知使用方：恢复期间服务不可用

### 4.2 恢复步骤

```bash
# 1. 停止服务
./scripts/stop.sh

# 2. 备份当前损坏数据（便于事后分析）
mv ./data/http-ingestion-meta.mv.db ./data/http-ingestion-meta.mv.db.broken.$(date +%s) 2>/dev/null || true

# 3. 解压备份到 data 目录
tar -xzf backup/http-ingestion-meta-YYYYMMDD-HHMMSS.tar.gz -C ./data

# 4. 启动并验证
./scripts/start.sh
./scripts/status.sh
```

```powershell
.\scripts\stop.ps1
Rename-Item "data\http-ingestion-meta.mv.db" "http-ingestion-meta.mv.db.broken.$(Get-Date -UFormat %s)" -ErrorAction SilentlyContinue
Expand-Archive -Path "backup\http-ingestion-meta-YYYYMMDD-HHMMSS.zip" -DestinationPath "data" -Force
.\scripts\start.ps1
.\scripts\status.ps1
```

### 4.3 恢复后验证清单

| # | 检查项 | 预期 |
|---|--------|------|
| 1 | `/actuator/health` | 状态 `UP`，H2 探针正常 |
| 2 | 连接器列表 | 数量与备份前一致 |
| 3 | 增量水位 | `connector_state` 值与预期一致 |
| 4 | 调度状态 | 已发布连接器调度已注册 |
| 5 | 试请求 | 任选一连接器试请求成功 |
| 6 | 手动同步 | 「立即增量同步」可完成且水位推进 |
| 7 | 运行历史 | 历史 `job_run` 记录可查询 |

全部通过 → 恢复成功。  
任一项失败 → 见 §5 故障处理。

---

## 5. 故障处理

### 5.1 H2 文件损坏

**现象**：启动失败、日志出现 `File corrupted`、health 中 H2 探针 DOWN。

**处理**：

1. 停止服务
2. 保留损坏文件（`.broken` 后缀）
3. 从最近冷备份恢复（§4）
4. 若无可用备份：仅可重建空库（**丢失全部配置与水位**），参考 §5.3

### 5.2 恢复后 Flyway 迁移失败

**现象**：启动报错 `FlywayException`、`Validate failed`。

**原因**：备份来自旧版 schema，当前 jar 版本更新。

**处理**：

1. 确认 release notes 是否有破坏性迁移
2. 使用与备份时**相同或兼容**的 jar 版本启动
3. 升级路径：先恢复 → 旧版启动成功 → 再按发布流程升级 jar

> 禁止在生产环境手动修改 `flyway_schema_history` 除非有明确运维方案。

### 5.3 无备份时的灾难重建

仅当无任何可用备份且接受数据丢失：

```bash
./scripts/stop.sh
rm -f ./data/http-ingestion-meta.*
./scripts/start.sh
```

后果：

- 所有连接器配置、水位、运行历史清空
- 需从导出配置（若有）或手工重新创建连接器
- 业务 PG 中已有数据**不受影响**，但增量水位丢失可能导致**重复 upsert**（通常可接受）或需手动全量重跑

**预防**：启用 §3 定期冷备份；重大配置变更前手动备份。

### 5.4 水位与业务数据不一致

**现象**：恢复旧备份后，水位落后于 PG 中实际数据。

**处理**（按场景选一）：

| 场景 | 建议 |
|------|------|
| PG 数据更新、可重复 upsert | 保持当前水位，继续增量 |
| 可能漏数 | 重置水位 → 触发增量（带 overlap）或全量 |
| 不确定 | 采样试运行 + 对比 PG 行数后再决定 |

---

## 6. 演练计划

每季度至少执行 **1 次**恢复演练（非生产环境或生产维护窗口）。

### 6.1 演练检查表

| 步骤 | 操作 | 结果 | 操作人 | 日期 |
|------|------|------|--------|------|
| 1 | 记录当前连接器数、样本水位 | | | |
| 2 | 执行冷备份 | | | |
| 3 | 停服并移除 H2 文件（模拟灾难） | | | |
| 4 | 从备份恢复 | | | |
| 5 | 执行 §4.3 验证清单 | | | |
| 6 | 记录耗时与问题 | | | |

**通过标准**：§4.3 全部 7 项通过，耗时 < 15 分钟（视数据量调整）。

---

## 7. 与外部 PG 备份的关系

```
┌─────────────────────┐         ┌─────────────────────┐
│  H2 元库备份         │         │  外部 PG 备份        │
│  (本指南)            │         │  (DBA 策略)          │
│  配置 + 水位 + 日志  │         │  业务表数据          │
└─────────────────────┘         └─────────────────────┘
```

完整灾难恢复可能需要**同时**恢复：

- H2：恢复「怎么同步、同步到哪里、水位在哪」
- PG：恢复「已落地的业务数据」

两者备份时间点差异越大，恢复后不一致风险越高。建议在**同一维护窗口**内先后备份。

---

## 8. 相关文档

- 设计规格：`docs/superpowers/specs/2026-06-05-http-ingestion-service-design.md`
- 监控告警：`docs/ops/monitoring.md`
- 启停脚本：`scripts/README.md`
