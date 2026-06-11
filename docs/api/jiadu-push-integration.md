# 佳都推理平台 — 事件推送集成

> 接口文档：《推理平台接口-260319.docx》  
> **§2.2.3** 订阅事件推送 + **§3 对象定义**

## 推送地址

佳都在 `EventRegQo.HttpAddressMaster` 注册：

```
POST http://{host}:{port}/ingress/{connectorId}
```

- Content-Type: `application/json`
- 请求头：`x_request_id`（可选，用于排错详情）；`sign` = MD5(`platFlag` + `_` + `EventID`) 32 位大写
- SLA：响应 ≤ 1 分钟；超时平台重试

**sign 校验默认关闭**（连接器配置 `webhook.verify_sign=false`）。开启后缺少或错误的 `sign` 返回 `OpCode≠0`，不写 Sink。

## 请求体 — EventInfo（§3）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| EventID | String | 是 | 事件 ID，幂等键 |
| EventType | Int | 是 | 算法 ID |
| EventName | String | 是 | 算法名称 |
| SendTime | String | 是 | 推送时间 |
| CameraID | String | 是 | 摄像头国标编号 |
| ImgUrl | String | 是* | 报警原图 URL |
| ImageUrl | String | 是* | §2.2.3 示例字段名，与 ImgUrl 二选一 |
| VideoUrl | String | 否 | 录像 URL |
| EventTime | String | 是 | 事件时间 |
| Confidence | Float | 是 | 可信度 |
| TaskID | String | 是 | 分析任务号 |
| EventGroup | Int | 是 | 1=告警 2=统计 |
| Census | Int | 是 | 检出数量 |
| InterDay | Int | 是 | 跨天标志 |
| EnterNumber | Int | 是 | 进入人数 |
| OutNumber | Int | 是 | 离开人数 |

实现归一化：`ImageUrl` → 内部 `ImgUrl`，再映射到 PG 列 `img_url`。

### sign 计算示例

```
platFlag = "ivsp"
EventID  = "UUID00001"
sign     = MD5("ivsp_UUID00001") = "CB0498DC24B6898EE2248257ECD4A01C" (大写)
```

## 响应体 — ResultInfo（§3）

**不是**通用 `ResponseResult`。

```json
{
  "OpCode": 0,
  "OpDesc": "成功"
}
```

| OpCode | 含义 |
|--------|------|
| 0 | 成功，事件已同步写入 Sink |
| 非 0 | 校验/业务失败（如连接器不存在、sign 失败、Transform 失败） |

HTTP 状态码始终为 **200**；失败时仍会创建 `job_run`（`run_type=push`，`status=failed`）。

## 订阅注册 — EventRegQo（§3，参考）

MVP **不实现** `regSubscribe` 客户端；由佳都侧或运维手工配置 `HttpAddressMaster` 指向本服务 ingress URL。

| 字段 | 说明 |
|------|------|
| HttpAddressMaster | 主推送地址（必填） |
| HttpAddressStandby | 备推送地址 |
| EventType / CameraID / EventGroups | 过滤条件 |
| SubBeginTime / SubEndTime | 订阅有效期 |

## 连接器配置（模板 `jiadu-event-push`）

```json
{
  "webhook": {
    "enabled": true,
    "path_suffix": "",
    "verify_sign": false,
    "plat_flag": "ivsp"
  },
  "transform": { "...": "EventInfo → jiadu_event_info 列映射" },
  "sink": {
    "type": "postgresql",
    "target": { "table": "jiadu_event_info" },
    "keys": ["event_id"]
  },
  "schedule": { "enabled": false }
}
```

- 连接器 `mode` 必须为 `push` 或 `both`
- 处理为**同步**写 PG：成功后才返回 `OpCode=0`

## 落库表

`jiadu_event_info`（DDL 见 `deploy/init-pg.sql`）。主键 `event_id`；`raw_json` 保存归一化后的 EventInfo JSON。

## 测试

| 层级 | 位置 | 场景 |
|------|------|------|
| 单元 | `JiaduSignVerifierTest` | MD5 sign 与大小写校验 |
| Java E2E | `HttpIngestionE2ETest.JiaduPushIngress` | P1 10 条、P2 幂等、P3 sign、P4 ImageUrl |
| Mock | `POST /mock/jiadu/push/{connectorId}?rounds=N&sign=&platFlag=` | 批量 POST ingress |
| Playwright | `e2e/jiadu-push.spec.ts` | 模板创建 → simulator → 详情页 push job |

本地命令：

```bash
./mvnw -pl http-ingestion-boot -am test \
  -Dtest=HttpIngestionE2ETest,JiaduSignVerifierTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
