# 据衡 R1 API Contract

- 文档状态：Baselined
- 版本：1.0
- 日期：2026-09-23
- 事实来源：当前 `main` 已实现的 Spring MVC Controller 与 Web DTO

## 1. 使用规则

本文记录 R1 React 客户端可以依赖的已实现 HTTP Contract。除“计划中的 Contract”章节外，路径、字段、状态码和错误码均来自当前代码；如果本文与实现不一致，必须停止前端扩展、核对测试并修正文档或通过明确 Story 变更 Contract，不能让客户端自行猜测。

所有业务 API：

- Base URL：`/api`
- Content Type：JSON；提交文件尚未实现。
- 认证：无状态 HTTP Basic；只有 `/actuator/health` 公开。
- 日期：`LocalDate` 使用 `YYYY-MM-DD`。
- 时间：`Instant` 使用带 UTC/offset 的 ISO-8601 字符串。
- UUID：JSON 字符串。
- 金额与数量：JSON number；前端只展示服务端结果，不用 JavaScript 浮点数重新决定权威总额。
- 未列出的写方法不属于公开 Contract。

## 2. 已实现端点总览

| Method | Path | 角色/范围 | 成功状态 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/api/current-user` | 已认证 | 200 | 当前可信用户和完整角色集合 |
| POST | `/api/procurement-requests` | `REQUESTER` | 201 | 创建采购草稿 |
| GET | `/api/procurement-requests` | `REQUESTER`，本人范围 | 200 | 分页查看自己的申请 |
| GET | `/api/procurement-requests/{requestId}` | `REQUESTER`，创建者 | 200 | 查看自己的申请详情 |
| PUT | `/api/procurement-requests/{requestId}` | `REQUESTER`，创建者、`DRAFT` | 200 | 以完整快照修改草稿 |
| POST | `/api/procurement-requests/{requestId}/submit` | `REQUESTER`，创建者、`DRAFT` | 200 | 幂等提交并创建审批任务 |
| GET | `/api/approval-tasks` | `APPROVER`，受理人范围 | 200 | 分页查看分配任务 |
| GET | `/api/approval-tasks/{taskId}` | `APPROVER`，任务受理人 | 200 | 查看任务及完整申请 |
| POST | `/api/approval-tasks/{taskId}/approve` | `APPROVER`，任务受理人、`PENDING` | 200 | 幂等批准 |
| POST | `/api/approval-tasks/{taskId}/reject` | `APPROVER`，任务受理人、`PENDING` | 200 | 幂等驳回 |
| GET | `/api/procurement-requests/{requestId}/audit-events` | 创建者或任务受理人 | 200 | 读取只读审计轨迹 |

角色只表示进入用例的必要条件，不替代所有权、任务归属、状态、版本和幂等校验。`ADMIN` 不自动获得申请或审批数据范围。

## 3. 公共结构

以下 TypeScript 只表达 JSON 形状，不是生成代码，也不表示前端可以改变服务端枚举。

```ts
type Role = 'REQUESTER' | 'APPROVER' | 'ADMIN'
type ProcurementRequestStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'
type ApprovalTaskStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
type ApprovalDecision = 'APPROVED' | 'REJECTED'
type CategoryCode = 'LAPTOP' | 'MONITOR' | 'OFFICE_CHAIR' | 'SOFTWARE_LICENSE'

interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface ApiErrorResponse {
  code: ApiErrorCode
  message: string
  path: string
  fieldErrors: FieldViolation[]
}

interface FieldViolation {
  field: string
  code: string
  message: string
}
```

当前错误响应没有 `timestamp`、`traceId` 或内部异常信息，前端不得假设这些字段存在。

## 4. 身份 Contract

### GET `/api/current-user`

```ts
interface CurrentUserResponse {
  userId: string
  roles: Role[]
}
```

角色按稳定名称顺序返回。请求体、Query 或自定义 Header 中的用户声明不能覆盖 HTTP Basic 认证结果。

## 5. 采购申请 Contract

### 5.1 请求结构

```ts
interface ProcurementItemWrite {
  name: string                 // required, max 200
  categoryCode: CategoryCode
  specification: string        // required, max 2000
  quantity: number             // > 0, integer <= 7 digits, fraction <= 4
  unit: string                 // required, max 40
  estimatedUnitPrice: number   // >= 0, integer <= 8 digits, fraction <= 2
}

interface CreateProcurementRequestRequest {
  title: string                // required, max 200
  purpose: string              // required, max 2000
  department: string           // required, max 100
  expectedDeliveryDate: string // LocalDate
  items: ProcurementItemWrite[] // 1..100
}

interface UpdateProcurementRequestRequest extends CreateProcurementRequestRequest {
  version: number              // integer >= 0
}

interface SubmitProcurementRequestRequest {
  version: number              // integer >= 0
}
```

客户端不能提交或覆盖 `id`、`businessNumber`、`creatorId`、`currency`、`estimatedTotal`、`status`、服务端时间或采购项行金额。

### 5.2 响应结构

```ts
interface ProcurementItemResponse {
  id: string
  lineNumber: number
  name: string
  categoryCode: CategoryCode
  specification: string
  quantity: number
  unit: string
  estimatedUnitPrice: number
  estimatedLineTotal: number
}

interface ProcurementRequestDetailResponse {
  id: string
  businessNumber: string
  creatorId: string
  title: string
  purpose: string
  department: string
  expectedDeliveryDate: string
  currency: 'CNY'
  estimatedTotal: number
  status: ProcurementRequestStatus
  version: number
  createdAt: string
  updatedAt: string
  items: ProcurementItemResponse[]
}

interface ProcurementRequestSummaryResponse {
  id: string
  businessNumber: string
  title: string
  department: string
  expectedDeliveryDate: string
  currency: 'CNY'
  estimatedTotal: number
  status: ProcurementRequestStatus
  version: number
  createdAt: string
  updatedAt: string
}
```

创建响应与详情响应字段基本相同，但创建响应只提供 `createdAt`，没有 `updatedAt`。前端应使用独立类型或显式转换，不能谎称字段一定存在。

### 5.3 创建

`POST /api/procurement-requests`

- Body：`CreateProcurementRequestRequest`
- 成功：201 + 创建响应。
- 当前没有 `Idempotency-Key`；客户端必须在请求进行中禁用重复点击，但不能把这种 UI 防抖描述为服务端幂等。

### 5.4 列表

`GET /api/procurement-requests?page=0&size=20&status=DRAFT`

- `page` 默认 0，最小 0。
- `size` 默认 20，范围 1..100。
- `status` 可省略；合法值为申请状态枚举。
- 成功：`PageResponse<ProcurementRequestSummaryResponse>`。
- SQL 已限制为可信当前创建者，前端不传 `creatorId`。

### 5.5 详情

`GET /api/procurement-requests/{requestId}`

- 成功：`ProcurementRequestDetailResponse`。
- 不存在与非创建者统一为 `RESOURCE_NOT_FOUND`，前端不得显示“该资源属于其他用户”。

### 5.6 更新

`PUT /api/procurement-requests/{requestId}`

- Body：`UpdateProcurementRequestRequest`，表达完整可编辑快照替换。
- 成功：`ProcurementRequestDetailResponse`，包含服务端新版本和重算金额。
- 陈旧版本：`CONCURRENT_MODIFICATION`。
- 非 `DRAFT` 等业务状态冲突：`BUSINESS_CONFLICT`。

### 5.7 提交

`POST /api/procurement-requests/{requestId}/submit`

- Header：`Idempotency-Key`，必填、非空、最大 64 字符。
- Body：`SubmitProcurementRequestRequest`。

```ts
interface SubmitProcurementRequestResponse {
  requestId: string
  requestStatus: 'SUBMITTED'
  requestVersion: number
  approvalTaskId: string
  approvalTaskStatus: 'PENDING'
}
```

相同调用者、申请、键和载荷的已完成重放返回第一次结果；处理中返回 `IDEMPOTENCY_IN_PROGRESS`；同键不同载荷返回 `IDEMPOTENCY_CONFLICT`。

## 6. 审批任务 Contract

### 6.1 列表

`GET /api/approval-tasks?page=0&size=20&status=PENDING`

- `page` 默认 0，最小 0。
- `size` 默认 20，范围 1..100。
- `status` 可省略；合法值为任务状态枚举。
- 查询只返回可信当前用户受理的任务，不接受 `assigneeId` Query。

```ts
interface ApprovalTaskSummaryResponse {
  id: string
  status: ApprovalTaskStatus
  version: number
  createdAt: string
  updatedAt: string
  procurementRequest: {
    id: string
    businessNumber: string
    creatorId: string
    title: string
    department: string
    expectedDeliveryDate: string
    currency: 'CNY'
    estimatedTotal: number
    status: ProcurementRequestStatus
    version: number
  }
}
```

成功响应为 `PageResponse<ApprovalTaskSummaryResponse>`。

### 6.2 详情

`GET /api/approval-tasks/{taskId}`

```ts
interface ApprovalTaskDetailResponse {
  id: string
  assigneeId: string
  status: ApprovalTaskStatus
  version: number
  createdAt: string
  updatedAt: string
  procurementRequest: ProcurementRequestDetailResponse
}
```

详情只对任务受理人可见；不存在与未分配统一为 `RESOURCE_NOT_FOUND`。当前响应不包含已经保存的 `ApprovalDecision`。

### 6.3 批准

`POST /api/approval-tasks/{taskId}/approve`

```ts
interface ApproveApprovalTaskRequest {
  approvalTaskVersion: number // integer >= 0
  comment?: string | null     // max 2000
}
```

### 6.4 驳回

`POST /api/approval-tasks/{taskId}/reject`

```ts
interface RejectApprovalTaskRequest {
  approvalTaskVersion: number // integer >= 0
  comment: string             // required, max 2000
}
```

批准与驳回都要求 `Idempotency-Key` Header，并返回：

```ts
interface ApprovalDecisionResponse {
  approvalTaskId: string
  approvalTaskStatus: 'APPROVED' | 'REJECTED'
  approvalTaskVersion: number
  decisionId: string
  decision: ApprovalDecision
  actorId: string
  decidedAt: string
  comment: string | null
}
```

该响应只在命令成功或相同幂等请求重放时取得。当前查询详情无法在刷新后恢复此结构，见第 9 节。

## 7. 审计 Contract

`GET /api/procurement-requests/{requestId}/audit-events`

```ts
interface ProcurementAuditTrailResponse {
  procurementRequestId: string
  events: AuditEventResponse[]
}

interface AuditEventResponse {
  id: string
  actorId: string
  action: string
  targetType: string
  targetId: string
  timestamp: string
  result: string
  requestIdentifier: string
}
```

- 只允许申请创建者或关联任务受理人读取。
- 事件已经按 `timestamp ASC, id ASC` 对应的服务端稳定顺序返回，前端不重新排序。
- 端点只支持 GET；POST、PUT、PATCH、DELETE 不属于 Contract，并返回统一 405。

## 8. 错误 Contract

| HTTP | `code` | 前端基本处理 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 请求 JSON/参数格式错误，显示安全通用消息 |
| 400 | `VALIDATION_FAILED` | 映射 `fieldErrors`，其余显示表单级错误 |
| 401 | `AUTHENTICATION_REQUIRED` | 清理内存凭据、Session 和 API Cache，进入登录页 |
| 403 | `ACCESS_DENIED` | 显示无权，不自动退出 |
| 404 | `RESOURCE_NOT_FOUND` | 显示不存在或不可访问，不推断所有者 |
| 405 | `METHOD_NOT_ALLOWED` | 记录客户端 Contract 错误并显示安全消息 |
| 409 | `APPROVAL_ROUTING_FAILED` | 提交失败，申请保持草稿，提示无可用唯一审批人 |
| 409 | `IDEMPOTENCY_CONFLICT` | 停止重试，不能偷偷生成新键重发不同载荷 |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | 保留原键，提示处理中并允许稍后重试相同载荷 |
| 409 | `BUSINESS_CONFLICT` | 重新读取资源并展示最新业务状态 |
| 409 | `CONCURRENT_MODIFICATION` | 编辑页保护本地内容，其他页面重新读取最新版本 |
| 500 | `INTERNAL_ERROR` | 显示通用失败和 `path`；当前没有 `traceId` |

Axios 自身的网络、超时和取消错误不伪装成上述后端错误。写请求遇到网络或超时无法确认结果时，不自动重试；用户以相同意图重试必须复用原幂等键。

## 9. 计划中的 Contract：US-017

以下接口尚未实现，前端在 `US-017` 合并前不得调用或使用 MSW 假装其已经存在。

建议 Contract：

`GET /api/procurement-requests/{requestId}/approval-decision`

- 允许申请创建者或关联任务受理审批人读取。
- 无最终决定、申请不存在和无权访问使用经 Story 测试确认的稳定不可枚举语义。
- 只读，不提供更新、覆盖或删除方法。

```ts
interface FinalApprovalDecisionResponse {
  procurementRequestId: string
  approvalTaskId: string
  decisionId: string
  decision: ApprovalDecision
  actorId: string
  decidedAt: string
  comment: string | null
}
```

最终路径、无结果语义和 HTTP 状态必须在 `US-017` 文件级实施计划中确认，并以实现及自动化测试回写本文。

## 10. Contract 变更规则

- 任何字段、枚举、路径、状态码、Header 或授权范围变化必须由对应 Story 驱动。
- 前端类型必须忠实描述响应，不能把可空字段改成必填，也不能补造后端未返回字段。
- 新增字段应评估旧客户端兼容；删除或改名是破坏性变化，必须显式迁移。
- R1 不引入 OpenAPI 代码生成；如果维护成本或漂移问题证明有必要，再建立独立 Enabler，而不是在当前文档任务中增加依赖。
