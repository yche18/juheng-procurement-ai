# 据衡 R1 Demo Baseline 验收记录

- 验收任务：`FE-017 R1 端到端验收`
- 验收日期：2026-09-27
- 验收状态：通过，形成 `v0.1.0` 候选基线
- 发布状态：未创建 Tag，未发布 GitHub Release；需在 PR 合并后由项目所有者明确确认

## 1. 验收环境

| 组件 | 实际环境 |
| --- | --- |
| 后端 | Java 17.0.20、Spring Boot 4.1.1 |
| 数据库 | PostgreSQL 17.11，Docker Compose 本地实例 |
| 前端 | Node.js 24.15.0、npm 11.6.2、Vite 8.3.0 |
| 浏览器验收 | Playwright 1.63.0、Chromium 153，Docker 内单 worker |
| 操作系统 | Windows 宿主机 + Docker Desktop Linux 容器 |

浏览器容器在自身 `127.0.0.1` 启动同一份 Vite 前端，通过 `host.docker.internal` 访问宿主机真实 Spring Boot；Spring Boot 连接 Compose PostgreSQL。这样既保留浏览器 localhost 安全上下文中的 Web Crypto 行为，也没有用 Mock 替代业务后端或数据库。

## 2. Acceptance Criteria 结果

| 验收项 | 结果 | 自动化证据 |
| --- | --- | --- |
| `/login` 使用真实演示身份；多角色显示全部导航；刷新清空内存身份；重新登录读取持久化事实 | 通过 | `r1-workflow.spec.ts` 登录/刷新用例与批准主流程 |
| 错误密码停留在 React 登录页并显示页面内错误，不触发浏览器原生 HTTP Basic 认证窗口 | 通过 | `r1-boundaries.spec.ts` 错误密码用例与真实 401 响应头断言 |
| 401 清理会话并返回登录；403 保留身份并显示无权页面 | 通过 | `r1-boundaries.spec.ts` 401/403 用例 |
| 申请人创建、查看、编辑并提交申请 | 通过 | `r1-workflow.spec.ts` 批准主流程 |
| 审批人查看任务并批准；独立用例驳回且原因必填 | 通过 | `r1-workflow.spec.ts` 批准与驳回用例 |
| 申请人和受理审批人重新登录后读取最终决定及审计事实 | 通过 | 批准/驳回主流程和详情审计断言 |
| 校验、越权、资源不可见、非法状态、陈旧版本、重复写入 | 通过 | `r1-boundaries.spec.ts` 三个边界用例 |
| Loading、Empty、网络失败和安全通用错误 UI | 通过 | `r1-ui-states.spec.ts` 三个页面状态用例 |
| 前端 lint、typecheck、单元/组件测试和生产构建 | 通过 | 下节列出的四个独立命令 |
| 后端完整测试 | 通过 | Java 17 下 `mvnw.cmd clean test`，102 项 |
| 空环境启动与 E2E 复现步骤 | 通过 | 根目录 `README.md` |

## 3. 实际执行结果

| 命令 | 结果 |
| --- | --- |
| `docker compose run --rm frontend npm run lint` | 通过，0 warning |
| `docker compose run --rm frontend npm run typecheck` | 通过 |
| `docker compose run --rm frontend npm test` | 18 个文件、75 项通过 |
| `docker compose run --rm frontend npm run build` | 通过，处理 1606 个模块 |
| `docker compose --profile e2e run --rm e2e` | 10 项通过，43.7 秒 |
| `mvnw.cmd clean test`（Java 17） | 102 项通过，0 failure/error/skipped |

E2E HTML 报告输出到 `frontend/playwright-report/`。失败重试所需的截图、视频和 trace 输出到 `frontend/test-results/`；成功运行会清理旧失败结果。两个目录均被 Git 忽略。

## 4. 运行边界

- E2E 的申请、提交、批准、驳回、权限、状态、版本和幂等断言均经过真实 Spring Boot 与 PostgreSQL。
- Loading、Empty、网络失败和畸形 500 响应使用 Playwright 路由注入，只验证确定性的页面呈现，不把注入结果当作后端安全证据。
- 所有数据均为合成数据。E2E 不自动删除当前本地数据库中的 `FE017-` 申请，避免测试工具擅自执行破坏性清库。
- Playwright 固定单 worker，因为四个演示身份和本地数据库属于共享验收资源；并行执行会降低隔离性和可重复性。

## 5. 已知限制与发布决定

- R1 使用本地 HTTP Basic，凭据只存在运行时内存；刷新后回到登录页是已确认行为，不是生产认证方案。
- 生产静态托管、OIDC/OAuth2、HTTPS、Secret 管理、性能测试和部署不属于 FE-017。
- 生产构建仍提示主 JavaScript chunk 超过 500 kB；当前不影响 R1 Demo 验收，后续只有在真实性能目标支持时才引入代码拆分。
- 当前候选满足 `v0.1.0` 的功能与回归门槛。合并 PR 后，项目所有者应先检查本记录和 CI，再明确决定是否创建 `v0.1.0` Tag；不得由自动化在未确认时发布 Release。
