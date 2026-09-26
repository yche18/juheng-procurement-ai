# 据衡 R1 前端架构

- 文档状态：Baselined
- 版本：1.1
- 日期：2026-09-26
- 当前实现状态：`FE-000` 与 `FE-010` 已合并；`US-017` 最终决定只读 Contract 已实现并进入评审；列表、详情、编辑、提交、审批和审计页面尚未实现

## 1. 架构目标

R1 前端是现有 Spring Boot REST API 的演示客户端，负责把服务端事实转化为清晰、可操作的页面。它必须：

- 保持业务 Feature 和用户流程清晰可追踪。
- 统一处理认证、错误、Loading、缓存失效和请求取消。
- 不复制服务端领域规则，不把浏览器状态提升为业务事实。
- 支持未来 R2/R3 按垂直切片增加 Feature，但不提前创建空模块和依赖。
- 对项目所有者保持可学习性，避免同时引入多个解决同一问题的状态或网络方案。

## 2. 技术基线

| 能力 | 选择 | 当前用途 |
| --- | --- | --- |
| UI | React + TypeScript | 组件与严格类型 |
| Build | Vite | 本地开发、代理和生产静态构建 |
| Routing | React Router | 登录、申请人和审批人路由 |
| Component Library | Ant Design | 表单、表格、布局、反馈和可访问基础 |
| HTTP | Axios | 实例、Basic Auth、超时、取消和传输错误 |
| Global State | Redux Toolkit | Session 和真实跨路由客户端状态 |
| Server State | RTK Query | 查询、Mutation、缓存、Tag 失效和请求状态 |
| Unit/Component Test | Vitest + React Testing Library | 组件、Store 和用户行为 |
| HTTP Mock | MSW | 按 API Contract 模拟后端边界 |
| Browser E2E | Playwright（`FE-017` 才引入） | 真实前后端验收 |

不同时引入 TanStack Query、Axios 之外的第二 HTTP Client、Redux Saga、MobX、Zustand、React Hook Form 或自建 Design System。出现明确需求时另行评审。

## 3. 仓库与运行方式

前端放在当前仓库 `frontend/` 子目录，不移动已经稳定的 Maven/Spring Boot 根目录：

```text
juheng-backend/
├─ frontend/
├─ src/                 # Spring Boot
├─ docs/
├─ pom.xml
└─ compose.yaml
```

R1 本地运行三个独立进程：PostgreSQL、Spring Boot 和 Vite Dev Server。Vite Dev Server 默认通过 Docker Compose 运行，源码以 bind mount 挂载，`node_modules` 和 npm cache 使用 Docker volume；这样本机不需要安装前端 Node/npm，也不会混用 Windows 与 Linux 原生依赖。容器内 Vite 将 `/api` 代理到宿主机 Spring Boot，Windows/macOS 使用 `host.docker.internal`，Linux 由 Compose 的 `host-gateway` 映射提供同名地址。浏览器仍通过 source map 调试源码，容器内文件监听使用 polling 保证 bind mount 修改可触发 HMR。

该容器只用于本地开发和自动化检查，不代表生产部署方式。前端生产构建只验证静态产物；是否由 Spring Boot 托管、独立静态部署或通过反向代理发布留到 Demo/Deployment Story 决定。需要脱离 Docker 排查前端工具链时仍可原生运行，但必须使用仓库记录的 Node/npm 版本。

`FE-000` 在开发镜像、`.nvmrc` 和 `package.json` 中固定并记录 Node/npm 运行版本，提交 `package-lock.json`。依赖使用明确版本并由 lockfile 保证可复现，不使用运行时 CDN。

## 4. 代码组织

### 4.1 目标结构

```text
frontend/src/
├─ app/
│  ├─ AppProviders.tsx
│  ├─ hooks.ts
│  ├─ router.tsx
│  └─ store.ts
├─ features/
│  ├─ identity/
│  │  ├─ api/
│  │  ├─ model/
│  │  └─ pages/
│  ├─ procurement/
│  │  ├─ api/
│  │  ├─ components/
│  │  ├─ model/
│  │  ├─ pages/
│  │  └─ types/
│  ├─ approval/
│  └─ audit/
└─ shared/
   ├─ api/
   │  ├─ apiClient.ts
   │  ├─ apiError.ts
   │  ├─ axiosBaseQuery.ts
   │  ├─ baseApi.ts
   │  └─ credentials.ts
   ├─ components/
   └─ types/
```

这是按阶段形成的目标结构，不是 `FE-000` 必须一次创建的目录清单：

- `FE-000` 只创建 `app`、`identity` 和实际需要的 `shared` 文件。
- `FE-010` 已按创建草稿用例建立 `procurement` 的 API、Model、Components 和 Page；其余采购能力及 `approval`、`audit` 在对应任务启动时创建。
- `materials`、`analysis`、`agent` 只在 R2/R3 Story 到来时创建。
- 空目录、空 Slice、占位 Endpoint 和“未来可能用到”的组件不提交。

### 4.2 依赖规则

```text
app
├─> features
└─> shared

features ──> shared
shared   ──X──> features / app
```

- `app` 负责组合 Provider、Store、Router 和跨 Feature 监听器。
- Feature 页面依赖本 Feature API、Model 和 Components，不直接散落 Axios 调用。
- `shared` 不包含采购、审批或角色业务判断。
- 一个 Feature 只有在后端 Contract 明确嵌套另一 Feature 类型时，才可通过对方公开类型入口依赖，不得导入对方页面、内部 Slice 或私有组件。
- 组件只有出现真实第二个使用方后才提升到 `shared/components`。
- `utils.ts`、`helpers.ts`、全局 `services/` 不作为杂物收容目录；命名必须表达职责。

## 5. 状态所有权

| 状态 | 所有者 | 示例 |
| --- | --- | --- |
| 正式业务事实 | Spring Boot + PostgreSQL | 申请状态、任务状态、决定、审计 |
| 服务端响应副本 | RTK Query | 申请详情、任务列表、审计轨迹 |
| 已验证会话 | Redux `sessionSlice` | `currentUser`、角色、认证状态 |
| Basic Auth 凭据 | `credentials.ts` 内存闭包 | 登录用户名与密码 |
| 未提交表单 | Ant Design Form / 页面局部状态 | 创建、编辑、驳回原因 |
| 分页与筛选 | URL Search Params | `page`、`size`、`status` |
| 一次写操作幂等上下文 | Mutation 调用方 + `sessionStorage` 中非敏感操作记录（仅确有刷新恢复需求时） | key、操作类型、目标和请求指纹 |
| 临时 UI | 组件局部状态 | 对话框开关、展开行 |

禁止：

- 把密码放入 Redux 或浏览器持久化存储。
- 为申请列表、详情和表单再创建普通 `procurementSlice`，与 RTK Query Cache 形成双份数据。
- 从 Cache 推导并持久化新的业务事实。
- 使用 Redux 保存只属于单一组件的对话框或输入状态。

## 6. Redux Toolkit 与 RTK Query

### 6.1 Store

`app/store.ts` 使用 `configureStore`，初始只注册：

- `sessionSlice.reducer`。
- `baseApi.reducer`。
- `baseApi.middleware`。
- 应用级 Listener Middleware，用于统一处理认证失效等跨边界事件。

`app/hooks.ts` 导出类型化 `useAppDispatch` 和 `useAppSelector`。组件不直接重复声明 `RootState` 或 `AppDispatch`。

### 6.2 Base API

`shared/api/baseApi.ts` 每个后端 Base URL 只创建一个 `createApi` 实例。各 Feature 使用 `injectEndpoints` 增加自己的查询和 Mutation：

```text
baseApi
├─ identityApi
├─ procurementApi
├─ approvalApi
└─ auditApi
```

Tag 建议：

- `CurrentUser`
- `ProcurementRequest`：列表使用 `LIST + filter`，详情使用 ID。
- `ApprovalTask`：列表和任务 ID。
- `ApprovalDecision`：申请 ID。
- `AuditTrail`：申请 ID。

Mutation 成功后只失效受影响的 Tag，不使用 `resetApiState` 清空全部 Cache；退出登录例外，必须清空所有用户范围数据。

### 6.3 不使用普通 Async Thunk 复制网络生命周期

普通 REST 查询和 Mutation 使用 RTK Query，不为每个端点重复建立 `createAsyncThunk`、`isLoading`、`error` 和实体数组。`createAsyncThunk` 只在未来确有多步纯客户端/跨端点编排且 RTK Query Lifecycle 无法清晰表达时评审使用。

## 7. Axios 边界

### 7.1 API Client

`shared/api/apiClient.ts` 创建唯一 Axios Instance，负责：

- `baseURL` 指向 `/api`。
- JSON Header 和响应。
- 有界超时。
- 从 `credentials.ts` 读取内存凭据并设置 Basic Auth Header。
- 接收 RTK Query 提供的 `AbortSignal`。

`apiClient.ts` 不导入 Redux Store，避免 Store → Base API → Axios → Store 的循环依赖。认证失效由 `axiosBaseQuery` 返回结构化错误，再由 `app` 层 Listener 处理。

### 7.2 凭据模块

`shared/api/credentials.ts` 只暴露最小操作：设置、读取认证头所需值和清理。内部变量不可由 Redux DevTools、持久化或序列化观察。登录流程：

```text
输入凭据
  -> 写入内存凭据
  -> 调用 GET /current-user
  -> 成功：保存 CurrentUser 到 sessionSlice
  -> 失败：清理内存凭据和 Session
```

退出流程：

```text
清理凭据
  -> sessionCleared
  -> baseApi.util.resetApiState()
  -> 跳转 /login
```

### 7.3 Axios Base Query

`shared/api/axiosBaseQuery.ts` 将 RTK Query 请求参数映射到 Axios，并只返回 RTK Query 接受的 `{data}` 或 `{error}`。错误必须经过 `apiError.ts` 标准化，保留后端 `code`、`message`、`path` 和 `fieldErrors`。

Axios Interceptor 不执行路由跳转、Toast、业务重试、状态转换、Tag 失效或 Redux Dispatch。这样避免隐藏副作用和测试间重复注册拦截器。

## 8. 错误模型

```ts
type FrontendApiError =
  | { kind: 'backend'; status: number; response: ApiErrorResponse }
  | { kind: 'network'; message: string }
  | { kind: 'timeout'; message: string }
  | { kind: 'cancelled' }
  | { kind: 'unexpected'; message: string }
```

规则：

- 业务分支依赖稳定 `code`，不解析自然语言 `message`。
- `fieldErrors` 由表单适配器集中映射；未知字段保留为表单级错误。
- `AUTHENTICATION_REQUIRED` 由 App Listener 清理 Session；`ACCESS_DENIED` 不清理。
- `cancelled` 不显示错误 Toast。
- `INTERNAL_ERROR` 不显示内部异常；当前 Contract 没有 `traceId`。
- 页面决定错误呈现方式，Axios 和 RTK Query 层不直接操作 Ant Design Message/Notification。

## 9. 写操作、版本与幂等

- 更新草稿始终携带详情响应的 `version`。
- 批准/驳回携带详情响应的 `approvalTaskVersion`。
- 提交和决定操作使用 `crypto.randomUUID()` 生成不超过 64 字符的幂等键。
- 一个键绑定调用者、操作、目标和载荷；相同意图网络重试复用，载荷或决定改变必须新建。
- Axios、RTK Query 和 Interceptor 都不自动重试 POST/PUT。
- 请求进行中禁用重复动作，但按钮禁用不被描述为幂等保证。
- 更新冲突时保留未提交表单；决定冲突时重新读取任务，不展示乐观成功。
- 前端不使用乐观更新改变采购或审批终态。

## 10. Router 与授权体验

`app/router.tsx` 定义公开登录路由和需要认证的应用壳。角色 Route Guard 只根据已验证的 `CurrentUser.roles` 决定导航体验：

- 未验证会话先验证，不闪现业务页面。
- 未认证进入 `/login`。
- 已认证但缺少页面角色进入 `/403`。
- 直接访问资源详情仍发出受保护 API 请求，由服务端校验数据范围。
- 多角色用户同时获得申请人和审批人导航。

Route Guard 不能接收 URL 或客户端参数中的角色声明，也不能把 `ADMIN` 当作所有业务数据的超级用户。

## 11. Form 与展示规则

- 使用 Ant Design Form，不同时引入第二套表单状态库。
- 字段长度、格式和必填校验与 `API_CONTRACT.md` 对齐，但服务端校验仍为最终结果。
- 数量和单价输入保留用户十进制文本直到提交映射；前端不计算权威金额。
- `LocalDate` 不应用时区偏移；`Instant` 按用户显示时区格式化，同时保留原始语义。
- 未知后端枚举不能静默映射为合法状态；页面显示安全兜底并记录 Contract 不匹配。
- 危险决定使用明确确认，不以颜色作为唯一提示。

## 12. 测试架构

### 12.1 `FE-000` 起具备

- Store 和 Session Slice 单元测试。
- 凭据不进入 Redux/持久化的测试。
- Axios Header、Timeout、AbortSignal 和错误标准化测试。
- RTK Query Base Query 成功/后端错误/网络错误测试。
- Login、Route Guard 和多角色导航组件测试。
- MSW Handler 与 `API_CONTRACT.md` 对齐。

### 12.2 每个 Feature Task

- 正常、Loading、Empty 和错误状态。
- 角色体验和服务端拒绝结果。
- Mutation 参数、版本与幂等 Header。
- Tag 失效和重新查询。
- 用户可见结果，不测试组件内部实现细节。

### 12.3 `FE-017`

才引入 Playwright，连接真实 Spring Boot 与 PostgreSQL，验证完整 R1 主路径和代表性失败路径。前端 E2E 不替代后端权限、事务、幂等和并发测试。

## 13. 依赖引入规则

每个新增 npm 依赖必须在当前任务计划中说明：

1. 解决的当前问题。
2. 为什么平台 API 或已有依赖不足。
3. 运行时还是开发时依赖。
4. 测试和维护边界。

R1 初始依赖应限制为已确认技术基线及其必要 Peer/测试依赖。图表、富文本、拖拽、日期大而全工具、CSS-in-JS 替代方案、国际化和状态持久化库不提前加入。

## 14. R2/R3 演进

R2/R3 延续 Feature-first 和垂直切片：

```text
Story / AC
  -> Backend Domain + Persistence + API
  -> API Contract
  -> Feature endpoint + page
  -> Integration / E2E
  -> Acceptance
```

- `materials` 在材料上传 Story 启动时创建。
- `analysis` 在 Analysis Run 有已确认 API 和页面状态时创建。
- `agent` 在 R3 Agent Run/Tool Call Contract 稳定后创建。
- 新 Feature 复用 Base API 和错误边界，但不能因“未来可能需要”预建 Store、Slice、Mock 或页面。

## 15. 已知限制

- R1 HTTP Basic 仅适合本地演示，刷新后需要重新登录。
- 完整最终决定使用 `GET /api/procurement-requests/{requestId}/approval-decision` 独立读取；尚无决定、不存在和越权统一为不可枚举的 404。
- 当前后端没有 OpenAPI 文档或生成类型，R1 使用人工维护并由测试核对的 Contract。
- 当前错误响应没有 `traceId`。
- R1 暂不决定静态资源生产托管方案。
