# Podman 一键部署

## 前置

- [Podman](https://podman.io/) 4+（Windows 建议 Podman Desktop + WSL2）
- JDK 21（仅构建 jar 时需要；运行时使用容器内 JRE）
- Maven wrapper：`./mvnw` 或 Windows 下 `.\mvnw-jdk21.ps1`

## 部署

应用**不构建镜像**，使用 `eclipse-temurin:21-jre-alpine` 挂载本地 jar：

```powershell
# Windows
.\scripts\podman\deploy.ps1

# 已构建 jar 时跳过 Maven
.\scripts\podman\deploy.ps1 -SkipBuild
```

```bash
# Linux / macOS
chmod +x scripts/podman/deploy.sh scripts/podman/teardown.sh
./scripts/podman/deploy.sh
```

访问 http://localhost:8080

## 服务

| 服务 | 镜像 | 端口 |
|------|------|------|
| postgres | postgres:16-alpine | 5432 |
| kafka | confluentinc/cp-kafka:7.6.1 (KRaft) | 9092 |
| http-ingestion | eclipse-temurin:21-jre-alpine + jar volume | 8080 |

`deploy/init-pg.sql` 含 example 模板表（`items`、`webhook_events` 等）。`deploy.ps1` 每次部署都会通过 `psql` 重新应用该脚本（幂等），避免仅首次 initdb 时建表导致 Playwright 同步失败。

环境变量见 `deploy/.env.example`（复制为 `deploy/.env` 可改端口）。

## 停止

```powershell
.\scripts\podman\teardown.ps1
```

```bash
./scripts/podman/teardown.sh
```

## Windows 说明

- 路径挂载依赖 Podman Machine 通过 `/mnt/<盘符>/...` 访问项目目录（drvfs）
- 若 health 检查失败，查看日志：`podman compose -f deploy/podman-compose.yml logs http-ingestion`

## 故障排查：statfs / input/output error

典型报错（`deploy.ps1` 或 `podman compose up`）：

```text
container create: statfs /mnt/d/.../deploy/init-pg.sql: input/output error
```

**根因**：Podman Machine（WSL）里的 Windows 盘符挂载（drvfs）处于僵死状态——`mount` 里能看到 `/mnt/c`、`/mnt/d`，但 `ls` 会报 `Input/output error`。与 compose 配置本身无关。

**快速修复**（本次已验证有效）：

```powershell
podman machine stop
podman machine start
.\scripts\podman\doctor.ps1
.\scripts\podman\deploy.ps1 -SkipBuild
```

`deploy.ps1` 在检测到 drvfs 不可读时会**自动尝试重启** Podman Machine 一次。

**仍失败时**：

1. Podman Desktop → Settings → Resources → 确认 D: 盘（或项目所在盘）已共享
2. 将仓库放到 `C:\` 或 WSL 原生路径（如 `\\wsl$\podman-machine-default\home\user\code\...`）
3. 完全重建 Machine（会清空 VM 内镜像缓存）：`podman machine rm -f podman-machine-default` 后由 Desktop 重新初始化

**诊断命令**：

```powershell
.\scripts\podman\doctor.ps1           # 检查 CLI、jar、drvfs
.\scripts\podman\doctor.ps1 -TryRepair  # 检查并尝试重启 Machine
wsl -d podman-machine-default -- ls /mnt/d/Work/99_Code/http-ingestion-service/deploy/
```
