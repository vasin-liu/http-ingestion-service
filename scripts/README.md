# HTTP Ingestion Service — 启停脚本

## 构建（JDK 21）

```powershell
# Windows（推荐，固定 JDK 21 + 本地 Maven 仓库）
.\mvnw-jdk21.ps1 -pl http-ingestion-boot -am package -DskipTests

# Linux / macOS
./mvnw -s .mvn/settings-jdk21.xml -pl http-ingestion-boot -am package -DskipTests
```

产物：

- Fat JAR：`http-ingestion-boot/target/http-ingestion-service.jar`
- 发布包：`http-ingestion-boot/target/http-ingestion-service.zip`（或 `.tar.gz`）

发布包目录结构：

```
http-ingestion-service/
├── bin/
│   ├── http-ingestion-service.jar
│   ├── run.sh / run.ps1 / run.bat
│   └── health-check.*
├── conf/
│   ├── application.yml
│   └── service.env.example
├── data/          # 运行时创建（H2 元库）
└── logs/          # 运行时创建
```

## 生产环境启停

解压 zip 后：

```bash
cp conf/service.env.example conf/service.env
# 编辑 HI_JAVA_HOME、HI_SERVER_PORT、EXTERNAL_PG_* 等

./bin/run.sh start
./bin/run.sh status
./bin/run.sh health
./bin/run.sh stop
./bin/run.sh restart
```

Windows：

```powershell
Copy-Item conf\service.env.example conf\service.env
.\bin\run.ps1 start
.\bin\run.ps1 status
```

## 本地开发启停

在项目根目录使用 `scripts/` 包装器（JAR 指向 `http-ingestion-boot/target/`，配置读取 `scripts/env`）：

```bash
cp scripts/env.example scripts/env
./scripts/run.sh start
./scripts/run.sh stop
./scripts/run.sh status
```

Windows：

```powershell
Copy-Item scripts\env.example scripts\env
.\scripts\run.ps1 start
```

等价命令：

| 旧脚本 | 新命令 |
|--------|--------|
| `start.*` | `run.* start` |
| `stop.*` | `run.* stop` |
| `status.*` | `run.* status` |
| `restart.*` | `run.* restart` |

## 配置说明

| 文件 | 用途 |
|------|------|
| `conf/service.env` | 打包部署配置（见 `service.env.example`） |
| `scripts/env` | 本地开发配置（见 `scripts/env.example`） |

主要变量：

- `HI_JAVA_HOME` / `JAVA_HOME` — JDK 21 路径
- `HI_SERVER_PORT` / `SERVER_PORT` — 服务端口（默认 8080）
- `HI_META_DB_PATH` / `META_DB_PATH` — H2 元数据库目录
- `EXTERNAL_PG_*` — 外部 PostgreSQL Sink（可选）
- `HI_DAEMON=0` — 前台运行（默认后台）

## Maven Wrapper

| 文件 | 说明 |
|------|------|
| `.mvn/settings-jdk21.xml` | 本地仓库与 HTTP blocker 配置 |
| `mvnw-jdk21.ps1` | Windows 下一键使用 JDK 21 构建 |
| `mvnw` / `mvnw.cmd` | 跨平台 Maven Wrapper |

## 故障排查

| 现象 | 处理 |
|------|------|
| JAR not found | 先 `mvnw-jdk21.ps1 ... package` |
| JDK 21 required | 在 `service.env` 设置 `HI_JAVA_HOME` |
| Health 超时 | 查看 `logs/http-ingestion-service.log` |
| 重复启动 | 先 `run.* stop` 或删除 `.pid` 文件 |
