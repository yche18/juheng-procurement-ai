# 据衡采购授权与证据决策平台

据衡是一个企业采购证据决策与授权平台。本仓库按 User Story 逐步交付；R1 Spring Boot 后端已完成服务启动、PostgreSQL/Flyway、统一 API 错误契约、本地演示身份、采购申请创建/查询/修改/提交、审批任务查询、人工批准/驳回和授权审计轨迹。当前阶段先建立 React 前端和端到端 Demo Baseline，再以垂直切片进入 R2 证据智能。

前端尚未初始化。R1 前端设计基线见：

- [`docs/R1_FRONTEND_UX.md`](docs/R1_FRONTEND_UX.md)
- [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md)
- [`docs/FRONTEND_ARCHITECTURE.md`](docs/FRONTEND_ARCHITECTURE.md)

现有后端启动和测试方式保持如下。

## 本地要求

- JDK 17
- Docker Desktop（或兼容的 Docker Engine 与 Compose）
- 可访问 Maven Central，首次运行 Maven Wrapper 时需要下载 Maven 和项目依赖

先确认终端实际使用的是 Java 17：

```shell
java -version
```

如果电脑安装了多个 JDK，请先在 IDE 或终端中把 `JAVA_HOME` 指向 JDK 17。

## 启动 PostgreSQL

项目提供本地开发用的 `compose.yaml`。其中的账号和密码只用于本机开发，均可通过应用环境变量覆盖。

```shell
docker compose up -d postgres
docker compose ps
```

`postgres` 显示为 `healthy` 后即可启动应用。数据库数据保存在 Docker volume 中，执行 `docker compose down` 只停止并移除容器，不会删除该 volume。

默认连接参数：

| 配置 | 默认值 | 环境变量 |
| --- | --- | --- |
| JDBC URL | `jdbc:postgresql://localhost:5432/juheng` | `JUHENG_DB_URL` |
| 用户名 | `juheng` | `JUHENG_DB_USERNAME` |
| 密码 | `juheng_local` | `JUHENG_DB_PASSWORD` |

## 启动应用

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```shell
./mvnw spring-boot:run
```

应用启动时 Flyway 会先校验并应用 `src/main/resources/db/migration` 下尚未执行的迁移。已成功执行的版本记录在 `flyway_schema_history` 中，重复启动不会重复执行。

应用默认监听 `http://localhost:8080`。服务启动后可检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

也可以使用：

```shell
curl http://localhost:8080/actuator/health
```

正常响应为：

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

`groups` 表示 Spring Boot 内置的存活与就绪检查分组。健康端点只返回分组名称和汇总状态，不公开组件详情、配置或凭据。

## 本地演示身份

US-003 使用无状态 HTTP Basic 提供最小可信认证上下文。该方案只服务于本地开发和自动化测试，不包含注册、密码管理、用户数据库或 OAuth2 身份供应商。除 `/actuator/health` 外的端点都需要认证。

| 用户名 | 角色 |
| --- | --- |
| `demo-requester` | `REQUESTER` |
| `demo-approver` | `APPROVER` |
| `demo-admin` | `ADMIN` |
| `demo-requester-approver` | `REQUESTER`、`APPROVER` |

四个身份的本地默认密码都是 `juheng-local`。可以在启动前通过 `JUHENG_DEMO_PASSWORD` 覆盖：

```powershell
$env:JUHENG_DEMO_PASSWORD = "replace-with-local-password"
.\mvnw.cmd spring-boot:run
```

查看认证上下文中的当前用户和完整角色集合：

```powershell
$credential = [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes("demo-requester:juheng-local")
)
Invoke-RestMethod `
    -Uri http://localhost:8080/api/current-user `
    -Headers @{ Authorization = "Basic $credential" }
```

也可以使用：

```shell
curl -u demo-requester:juheng-local http://localhost:8080/api/current-user
```

正常响应示例：

```json
{"userId":"demo-requester","roles":["REQUESTER"]}
```

业务代码只使用服务端认证上下文中的 `userId` 和角色集合；请求体、查询参数或自定义请求头中的身份声明都不会覆盖它。HTTP Basic 凭据只做本地演示，任何非本机部署都必须更换密码并使用 HTTPS，后续可在不改变应用层 `CurrentUser` 契约的前提下替换为正式身份供应商。

R1 的提交路由默认将任务分配给 `demo-approver`。可以使用逗号分隔的 `JUHENG_APPROVAL_ASSIGNEE_IDS` 覆盖候选人配置；提交时必须恰好解析出一个候选人，并且不能是申请创建者本人。空配置、多个不同候选人或自审都会失败关闭，不会把申请留在没有审批任务的 `SUBMITTED` 状态。

## 创建采购申请草稿

具有 `REQUESTER` 角色的用户可以调用 `POST /api/procurement-requests` 创建草稿。创建者、币种 `CNY`、状态 `DRAFT`、版本、业务编号、预计总额和审计字段均由服务端控制。

```shell
curl -u demo-requester:juheng-local \
  -H "Content-Type: application/json" \
  -d '{
    "title": "研发电脑采购",
    "purpose": "补充开发设备",
    "department": "研发部",
    "expectedDeliveryDate": "2026-10-01",
    "items": [
      {
        "name": "开发笔记本",
        "categoryCode": "LAPTOP",
        "specification": "32GB 内存",
        "quantity": 2,
        "unit": "台",
        "estimatedUnitPrice": 8999.00
      }
    ]
  }' \
  http://localhost:8080/api/procurement-requests
```

成功时返回 HTTP `201`。业务编号格式为 `PR-yyyyMMdd-序列值`；序列保证唯一但不承诺连续。当前受控品类为 `LAPTOP`、`MONITOR`、`OFFICE_CHAIR` 和 `SOFTWARE_LICENSE`。数量最多四位小数，单价和金额使用两位小数；每行按 `quantity × estimatedUnitPrice` 计算并以 `HALF_UP` 舍入，总额为各行舍入后金额之和。

申请、采购项和 `PROCUREMENT_REQUEST_CREATED` 审计事件在同一个 PostgreSQL 事务中保存：任一写入失败时全部回滚。创建接口本身不包含提交或审批能力。

## 查看自己的采购申请

具有 `REQUESTER` 角色的用户可以分页查看自己创建的申请。页码从 `0` 开始，默认每页 `20` 条，最大 `100` 条；结果固定按 `createdAt DESC, id DESC` 排序。`status` 可以省略，也可以使用 `DRAFT`、`SUBMITTED`、`APPROVED` 或 `REJECTED`：

```shell
curl -u demo-requester:juheng-local \
  "http://localhost:8080/api/procurement-requests?page=0&size=20&status=DRAFT"
```

分页响应包含 `content`、`page`、`size`、`totalElements` 和 `totalPages`。列表只读取申请摘要，不逐条加载采购项。

取得列表中的申请 ID 后，可以查看详情：

```shell
curl -u demo-requester:juheng-local \
  http://localhost:8080/api/procurement-requests/替换为申请UUID
```

详情包含采购项、服务端计算金额、当前状态和版本。数据库查询会直接使用认证上下文中的用户 ID 限定 `creator_id`；客户端不能通过请求参数指定查询所有者。他人申请与不存在的申请统一返回 `404 RESOURCE_NOT_FOUND`，避免泄露资源是否存在。

## 修改采购申请草稿

申请创建者可以使用 `PUT /api/procurement-requests/{requestId}` 整体替换仍处于 `DRAFT` 的可编辑字段和采购项。先通过详情接口取得当前 `version`，再把该版本随更新请求提交：

```shell
curl -u demo-requester:juheng-local \
  -X PUT \
  -H "Content-Type: application/json" \
  -d '{
    "version": 0,
    "title": "更新后的研发电脑采购",
    "purpose": "补充开发和测试设备",
    "department": "研发部",
    "expectedDeliveryDate": "2026-11-01",
    "items": [
      {
        "name": "开发笔记本",
        "categoryCode": "LAPTOP",
        "specification": "32GB 内存",
        "quantity": 2,
        "unit": "台",
        "estimatedUnitPrice": 8999.00
      }
    ]
  }' \
  http://localhost:8080/api/procurement-requests/替换为申请UUID
```

成功响应包含重新计算的行金额、总额和递增后的版本。该接口采用完整快照语义：请求中的 `items` 会替换原采购项，采购项 ID 和行号由服务端重新生成；业务编号、创建者、币种和状态不能由请求体改变。

修改时数据库使用申请 ID、可信创建者、`DRAFT` 状态和旧版本执行条件更新。旧版本请求返回 `409 CONCURRENT_MODIFICATION`，不会覆盖先完成的修改；非草稿申请返回 `409 BUSINESS_CONFLICT`。申请新版本、采购项、总额和 `PROCUREMENT_REQUEST_UPDATED` 审计事件在同一个事务中提交或回滚。

## 提交采购申请

申请创建者可以调用 `POST /api/procurement-requests/{requestId}/submit`，把完整 `DRAFT` 提交给人工审批。请求必须同时携带当前申请版本和最多 64 个字符的 `Idempotency-Key`：

```shell
curl -u demo-requester:juheng-local \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: submit-request-001" \
  -d '{"version": 0}' \
  http://localhost:8080/api/procurement-requests/替换为申请UUID/submit
```

成功响应示例：

```json
{
  "requestId": "替换为申请UUID",
  "requestStatus": "SUBMITTED",
  "requestVersion": 1,
  "approvalTaskId": "服务端生成的任务UUID",
  "approvalTaskStatus": "PENDING"
}
```

提交成功时，申请状态和版本、唯一审批任务、`PROCUREMENT_REQUEST_SUBMITTED` 与 `APPROVAL_TASK_ASSIGNED` 两条审计事件，以及可重放的幂等结果在同一个 PostgreSQL 事务中提交。任何一步失败都会整体回滚。

相同调用者使用相同幂等键和相同版本重试时，会返回首次创建的任务，不会重复写入状态、任务或审计。同一作用域下复用该键但改变版本会返回 `409 IDEMPOTENCY_CONFLICT`。不同幂等键并发提交同一草稿时，数据库的 `DRAFT + version` 条件更新保证最多一个成功。该接口只创建人工审批任务，不执行批准或驳回，也不调用 LLM。

## 查看待审批任务

具有 `APPROVER` 角色的用户可以分页查看分配给自己的审批任务。页码从 `0` 开始，默认每页 `20` 条，最大 `100` 条；结果固定按任务的 `createdAt DESC, id DESC` 排序。`status` 可以省略，也可以使用 `PENDING`、`APPROVED` 或 `REJECTED`：

```shell
curl -u demo-approver:juheng-local \
  "http://localhost:8080/api/approval-tasks?page=0&size=20&status=PENDING"
```

分页响应包含 `content`、`page`、`size`、`totalElements` 和 `totalPages`。每条任务包含任务状态、任务版本和采购申请摘要；列表先分页查询当前审批人的任务，再批量加载本页申请，不会逐条查询申请。

取得列表中的任务 ID 后，可以查看任务及完整采购申请详情：

```shell
curl -u demo-approver:juheng-local \
  http://localhost:8080/api/approval-tasks/替换为任务UUID
```

详情包含任务状态、受理人、任务版本、申请核心字段、申请版本以及按行号排序的采购项。数据库任务查询直接使用认证上下文中的用户 ID 限定 `assignee_id`；客户端不能指定查询审批人。他人任务与不存在的任务统一返回 `404 RESOURCE_NOT_FOUND`，并且不会返回关联申请内容。

## 人工批准或驳回

受理审批人可以对仍为 `PENDING` 的本人任务发出两个独立命令。两者都必须携带详情接口返回的 `approvalTaskVersion` 和最多 64 个字符的 `Idempotency-Key`；批准意见可选：

```shell
curl -u demo-approver:juheng-local \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: approve-task-001" \
  -d '{"approvalTaskVersion": 0, "comment": "同意采购"}' \
  http://localhost:8080/api/approval-tasks/替换为任务UUID/approve
```

驳回原因必填，空白原因返回 `400 VALIDATION_FAILED` 和 `comment` 字段错误：

```shell
curl -u demo-approver:juheng-local \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: reject-task-001" \
  -d '{"approvalTaskVersion": 0, "comment": "预算依据不足"}' \
  http://localhost:8080/api/approval-tasks/替换为任务UUID/reject
```

成功响应包含任务终态与新版本，以及唯一决定的 `decisionId`、`decision`、可信 `actorId`、服务端 `decidedAt` 和意见。任务终态、申请终态、唯一决定、审计事件和幂等结果处于同一个 PostgreSQL 事务；任一写入失败会整体回滚。

任务只按认证上下文中的受理人加载，申请人即使同时拥有 `APPROVER` 角色也不能审批自己的申请。相同调用者以相同键和载荷重试会返回第一次决定；同键改变决定、版本或意见返回 `409 IDEMPOTENCY_CONFLICT`；不同键并发批准和驳回时最多一个成功，失败方得到明确的业务或并发冲突。LLM 和 Agent 均没有调用这些命令的入口。

## 查看申请审计轨迹

申请创建者和该申请审批任务的受理人可以调用只读接口查看关键业务轨迹：

```shell
curl -u demo-requester:juheng-local \
  http://localhost:8080/api/procurement-requests/替换为申请UUID/audit-events
```

也可以由受理审批人使用相同路径查询：

```shell
curl -u demo-approver:juheng-local \
  http://localhost:8080/api/procurement-requests/替换为申请UUID/audit-events
```

响应按 `timestamp ASC, id ASC` 稳定排序，包含申请创建、修改、提交、任务分配和最终批准/驳回等已经发生的事件：

```json
{
  "procurementRequestId": "替换为申请UUID",
  "events": [
    {
      "id": "事件UUID",
      "actorId": "demo-requester",
      "action": "PROCUREMENT_REQUEST_CREATED",
      "targetType": "PROCUREMENT_REQUEST",
      "targetId": "替换为申请UUID",
      "timestamp": "2026-09-23T01:00:00Z",
      "result": "SUCCESS",
      "requestIdentifier": "服务端请求或幂等标识"
    }
  ]
}
```

Repository 查询同时携带可信当前用户 ID：只有 `creator_id` 或关联审批任务的 `assignee_id` 命中时才读取事件。其他业务用户与不存在的申请统一返回 `404 RESOURCE_NOT_FOUND`；`ADMIN` 不自动获得业务审计读取权限。响应只映射上述白名单字段，不包含认证凭据、内部异常或堆栈。

`requestIdentifier` 用于关联创建/修改请求或已经绑定调用者、操作和目标的幂等请求；它不是认证凭据，也不会赋予任何额外权限。客户端不应把密码、访问令牌或其他秘密放入 `Idempotency-Key`。

该路径只开放 `GET`。`POST`、`PUT` 和 `DELETE` 返回 `405 METHOD_NOT_ALLOWED`，普通业务 API 不提供修改或删除审计事实的能力。US-016 直接复用 V2 已建立的 `(procurement_request_id, occurred_at, id)` 索引，因此没有新增 Flyway 迁移。

## API 错误响应

API 使用稳定错误代码区分请求格式、字段校验、认证、授权、业务冲突和系统故障。最小响应结构如下：

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/example",
  "fieldErrors": [
    {
      "field": "title",
      "code": "NotBlank",
      "message": "must not be blank"
    }
  ]
}
```

| HTTP 状态 | 错误代码 | 含义 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 请求体格式错误 |
| `400` | `VALIDATION_FAILED` | 字段或参数不符合约束 |
| `401` | `AUTHENTICATION_REQUIRED` | 请求缺少有效认证身份 |
| `403` | `ACCESS_DENIED` | 当前身份没有操作权限 |
| `404` | `RESOURCE_NOT_FOUND` | 当前数据范围内不存在目标资源 |
| `405` | `METHOD_NOT_ALLOWED` | 目标资源不支持该 HTTP 方法 |
| `409` | `APPROVAL_ROUTING_FAILED` | 无法解析唯一且非申请人本人的审批人 |
| `409` | `IDEMPOTENCY_CONFLICT` | 同一幂等键已经绑定到不同载荷 |
| `409` | `IDEMPOTENCY_IN_PROGRESS` | 相同幂等请求尚未产生可重放结果 |
| `409` | `BUSINESS_CONFLICT` | 请求与当前业务状态冲突 |
| `409` | `CONCURRENT_MODIFICATION` | 客户端版本已过期或并发条件更新失败 |
| `500` | `INTERNAL_ERROR` | 未预期系统错误 |

错误响应不包含被拒绝的字段值、内部异常消息、堆栈或凭据。`fieldErrors` 只在校验失败时包含内容，其他错误返回空数组。Spring Security 过滤器产生的 401/403 与 Controller 内的统一错误契约保持一致。

## 运行测试

Windows PowerShell：

```powershell
.\mvnw.cmd clean test
```

macOS / Linux：

```shell
./mvnw clean test
```

完整测试包含基于 Testcontainers 的 PostgreSQL 集成测试，因此运行前需要启动 Docker。测试会自行创建和销毁临时 PostgreSQL 容器，不会使用或修改 `compose.yaml` 创建的本地数据库。

应用上下文和健康端点测试使用 `no-database` profile，继续保持为不依赖 PostgreSQL 的快速测试。Flyway 集成测试会验证空库依次应用 V1 至 V6、迁移校验、查询索引以及重复执行不会再次应用已有版本；采购申请、审批任务和审计轨迹集成测试会验证真实安全过滤器、MyBatis-Plus 持久化、字段校验、服务端受控字段、事务回滚、所有者/受理人范围、分页、详情查询、稳定审计排序、只读边界、版本条件更新、审批路由、幂等重放和并发冲突。
