# 据衡后端

据衡是一个企业采购证据决策与授权平台。本仓库当前按 User Story 逐步交付；目前已建立服务启动、PostgreSQL/Flyway、统一 API 错误契约、本地演示身份，以及创建采购申请草稿的首个业务垂直切片。

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

申请、采购项和 `PROCUREMENT_REQUEST_CREATED` 审计事件在同一个 PostgreSQL 事务中保存：任一写入失败时全部回滚。当前 Story 不包含草稿查询、修改、提交或审批接口。

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
| `409` | `BUSINESS_CONFLICT` | 请求与当前业务状态冲突 |
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

应用上下文和健康端点测试使用 `no-database` profile，继续保持为不依赖 PostgreSQL 的快速测试。Flyway 集成测试会验证空库依次应用 V1/V2、迁移校验以及重复执行不会再次应用已有版本；采购草稿集成测试会验证真实安全过滤器、MyBatis-Plus 持久化、字段校验、服务端受控字段和事务回滚。
