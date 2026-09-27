import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

const taskId = '30000000-0000-0000-0000-000000000001'
const requestId = '10000000-0000-0000-0000-000000000001'
const taskUrl = `${API_ORIGIN}/api/approval-tasks/${taskId}`
const approveUrl = `${taskUrl}/approve`
const rejectUrl = `${taskUrl}/reject`
const finalDecisionUrl =
  `${API_ORIGIN}/api/procurement-requests/${requestId}/approval-decision`
const auditUrl =
  `${API_ORIGIN}/api/procurement-requests/${requestId}/audit-events`

function approverStore() {
  const store = createAppStore()
  store.dispatch(
    sessionAuthenticated({
      userId: 'demo-approver',
      roles: ['APPROVER'],
    }),
  )
  return store
}

function taskDetail(overrides: Record<string, unknown> = {}) {
  return {
    id: taskId,
    assigneeId: 'demo-approver',
    status: 'PENDING',
    version: 2,
    createdAt: '2026-09-27T01:00:00Z',
    updatedAt: '2026-09-27T02:00:00Z',
    procurementRequest: {
      id: requestId,
      businessNumber: 'PR-20260927-1001',
      creatorId: 'demo-requester',
      title: '研发电脑采购',
      purpose: '补充开发设备',
      department: '研发部',
      expectedDeliveryDate: '2026-10-10',
      currency: 'CNY',
      estimatedTotal: 18000,
      status: 'SUBMITTED',
      version: 4,
      createdAt: '2026-09-27T00:00:00Z',
      updatedAt: '2026-09-27T01:00:00Z',
      items: [
        {
          id: '20000000-0000-0000-0000-000000000001',
          lineNumber: 1,
          name: '开发笔记本',
          categoryCode: 'LAPTOP',
          specification: '32GB 内存',
          quantity: 2,
          unit: '台',
          estimatedUnitPrice: 9000,
          estimatedLineTotal: 18000,
        },
      ],
    },
    ...overrides,
  }
}

function terminalTask(decision: 'APPROVED' | 'REJECTED', version = 3) {
  return taskDetail({
    status: decision,
    version,
    updatedAt: '2026-09-27T03:00:00Z',
    procurementRequest: {
      ...taskDetail().procurementRequest,
      status: decision,
      version: 5,
      updatedAt: '2026-09-27T03:00:00Z',
    },
  })
}

function decisionResponse(
  decision: 'APPROVED' | 'REJECTED',
  comment: string | null,
) {
  return {
    approvalTaskId: taskId,
    approvalTaskStatus: decision,
    approvalTaskVersion: 3,
    decisionId: '40000000-0000-0000-0000-000000000001',
    decision,
    actorId: 'demo-approver',
    decidedAt: '2026-09-27T03:00:00Z',
    comment,
  }
}

function finalDecision(
  decision: 'APPROVED' | 'REJECTED',
  comment: string | null,
) {
  const response = decisionResponse(decision, comment)
  return {
    procurementRequestId: requestId,
    approvalTaskId: response.approvalTaskId,
    decisionId: response.decisionId,
    decision: response.decision,
    actorId: response.actorId,
    decidedAt: response.decidedAt,
    comment: response.comment,
  }
}

function backendError(code: string, message: string, path = approveUrl) {
  return { code, message, path, fieldErrors: [] }
}

async function renderPending() {
  const view = renderApplication({
    initialEntries: [`/approver/tasks/${taskId}`],
    store: approverStore(),
  })
  expect(
    await screen.findByRole('button', { name: '批准申请' }),
  ).toBeEnabled()
  return view
}

async function approveWithComment(
  user: ReturnType<typeof userEvent.setup>,
  comment?: string,
) {
  await user.click(screen.getByRole('button', { name: '批准申请' }))
  if (comment) {
    await user.type(screen.getByLabelText('批准意见（可选）'), comment)
  }
  await user.click(screen.getByRole('button', { name: '确认批准' }))
}

describe('ApprovalDecisionPanel', () => {
  it('restores a terminal decision from the read-only endpoint after page entry', async () => {
    let finalDecisionRequestCount = 0
    server.use(
      http.get(taskUrl, () => HttpResponse.json(terminalTask('REJECTED'))),
      http.get(finalDecisionUrl, () => {
        finalDecisionRequestCount += 1
        return HttpResponse.json(
          finalDecision('REJECTED', '刷新后仍读取服务端原因'),
        )
      }),
    )

    renderApplication({
      initialEntries: [`/approver/tasks/${taskId}`],
      store: approverStore(),
    })

    expect(
      (await screen.findAllByText('已驳回（REJECTED）')).length,
    ).toBeGreaterThanOrEqual(2)
    expect(
      await screen.findByText('刷新后仍读取服务端原因'),
    ).toBeInTheDocument()
    expect(finalDecisionRequestCount).toBe(1)
    expect(screen.queryByRole('button', { name: '批准申请' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '驳回申请' })).not.toBeInTheDocument()
  })

  it('approves with the current task version and refreshes the persisted final decision', async () => {
    const user = userEvent.setup()
    let approved = false
    let taskRequestCount = 0
    let finalDecisionRequestCount = 0
    let auditRequestCount = 0
    let requestBody: unknown
    let idempotencyKey: string | null = null
    server.use(
      http.get(taskUrl, () => {
        taskRequestCount += 1
        return HttpResponse.json(
          approved ? terminalTask('APPROVED') : taskDetail(),
        )
      }),
      http.post(approveUrl, async ({ request }) => {
        requestBody = await request.json()
        idempotencyKey = request.headers.get('Idempotency-Key')
        approved = true
        return HttpResponse.json(decisionResponse('APPROVED', '同意采购'))
      }),
      http.get(finalDecisionUrl, () => {
        finalDecisionRequestCount += 1
        return HttpResponse.json(finalDecision('APPROVED', '同意采购'))
      }),
      http.get(auditUrl, () => {
        auditRequestCount += 1
        return HttpResponse.json({ procurementRequestId: requestId, events: [] })
      }),
    )

    await renderPending()
    await waitFor(() => expect(auditRequestCount).toBe(1))
    await approveWithComment(user, '  同意采购  ')

    expect(await screen.findByText('申请已批准')).toBeInTheDocument()
    expect(requestBody).toEqual({
      approvalTaskVersion: 2,
      comment: '同意采购',
    })
    expect(idempotencyKey).toMatch(/^.{1,64}$/)
    await waitFor(() => expect(taskRequestCount).toBeGreaterThanOrEqual(2))
    await waitFor(() => expect(finalDecisionRequestCount).toBe(1))
    await waitFor(() => expect(auditRequestCount).toBeGreaterThanOrEqual(2))

    const closeButton = screen.queryByRole('button', { name: '关闭' })
    if (closeButton) {
      await user.click(closeButton)
    }
    expect(
      (await screen.findAllByText('已批准（APPROVED）')).length,
    ).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByText('同意采购').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByRole('button', { name: '批准申请' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '驳回申请' })).not.toBeInTheDocument()
    expect(window.sessionStorage).toHaveLength(0)
  })

  it('requires a rejection reason and sends a distinct reject command', async () => {
    const user = userEvent.setup()
    let rejected = false
    let requestCount = 0
    let requestBody: unknown
    server.use(
      http.get(taskUrl, () =>
        HttpResponse.json(rejected ? terminalTask('REJECTED') : taskDetail()),
      ),
      http.post(rejectUrl, async ({ request }) => {
        requestCount += 1
        requestBody = await request.json()
        rejected = true
        return HttpResponse.json(decisionResponse('REJECTED', '预算依据不足'))
      }),
      http.get(finalDecisionUrl, () =>
        HttpResponse.json(finalDecision('REJECTED', '预算依据不足')),
      ),
    )

    await renderPending()
    await user.click(screen.getByRole('button', { name: '驳回申请' }))
    await user.click(screen.getByRole('button', { name: '确认驳回' }))
    expect(await screen.findByText('请输入驳回原因')).toBeInTheDocument()
    expect(requestCount).toBe(0)

    await user.type(screen.getByLabelText('驳回原因'), '  预算依据不足  ')
    await user.click(screen.getByRole('button', { name: '确认驳回' }))

    expect(await screen.findByText('申请已驳回')).toBeInTheDocument()
    expect(requestCount).toBe(1)
    expect(requestBody).toEqual({
      approvalTaskVersion: 2,
      comment: '预算依据不足',
    })
  })

  it('reuses the same key while an identical decision is still in progress', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    let requestCount = 0
    let approved = false
    server.use(
      http.get(taskUrl, () =>
        HttpResponse.json(approved ? terminalTask('APPROVED') : taskDetail()),
      ),
      http.post(approveUrl, ({ request }) => {
        requestCount += 1
        keys.push(request.headers.get('Idempotency-Key'))
        if (requestCount === 1) {
          return HttpResponse.json(
            backendError(
              'IDEMPOTENCY_IN_PROGRESS',
              'An identical request is still being processed',
            ),
            { status: 409 },
          )
        }
        approved = true
        return HttpResponse.json(decisionResponse('APPROVED', null))
      }),
      http.get(finalDecisionUrl, () =>
        HttpResponse.json(finalDecision('APPROVED', null)),
      ),
    )

    await renderPending()
    await approveWithComment(user)
    expect(
      await screen.findByText('相同审批决定正在处理中'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /原幂等键重试/ }))

    expect(await screen.findByText('申请已批准')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[1]).toBe(keys[0])
  })

  it('restores the same key after an uncertain network result and remount', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    let requestCount = 0
    let approved = false
    server.use(
      http.get(taskUrl, () =>
        HttpResponse.json(approved ? terminalTask('APPROVED') : taskDetail()),
      ),
      http.post(approveUrl, ({ request }) => {
        requestCount += 1
        keys.push(request.headers.get('Idempotency-Key'))
        if (requestCount === 1) {
          return HttpResponse.error()
        }
        approved = true
        return HttpResponse.json(decisionResponse('APPROVED', '同意采购'))
      }),
      http.get(finalDecisionUrl, () =>
        HttpResponse.json(finalDecision('APPROVED', '同意采购')),
      ),
    )

    const firstView = await renderPending()
    await approveWithComment(user, '同意采购')
    expect(await screen.findByText('无法确认审批结果')).toBeInTheDocument()
    firstView.unmount()

    await renderPending()
    await approveWithComment(user, '同意采购')

    expect(await screen.findByText('申请已批准')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[1]).toBe(keys[0])
  })

  it('requires explicit abandonment before changing to the opposite decision', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    let requestCount = 0
    let rejected = false
    server.use(
      http.get(taskUrl, () =>
        HttpResponse.json(rejected ? terminalTask('REJECTED') : taskDetail()),
      ),
      http.post(approveUrl, ({ request }) => {
        requestCount += 1
        keys.push(request.headers.get('Idempotency-Key'))
        return HttpResponse.json(
          backendError(
            'IDEMPOTENCY_CONFLICT',
            'Idempotency key conflicts with a different request',
          ),
          { status: 409 },
        )
      }),
      http.post(rejectUrl, ({ request }) => {
        requestCount += 1
        keys.push(request.headers.get('Idempotency-Key'))
        rejected = true
        return HttpResponse.json(decisionResponse('REJECTED', '依据不足'))
      }),
      http.get(finalDecisionUrl, () =>
        HttpResponse.json(finalDecision('REJECTED', '依据不足')),
      ),
    )

    await renderPending()
    await approveWithComment(user)
    expect(
      await screen.findByText('幂等键与既有审批请求冲突'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '确认批准' })).toBeDisabled()
    expect(requestCount).toBe(1)

    await user.click(
      screen.getByRole('button', { name: '放弃本次决定意图' }),
    )
    await user.click(screen.getByRole('button', { name: '驳回申请' }))
    await user.type(screen.getByLabelText('驳回原因'), '依据不足')
    await user.click(screen.getByRole('button', { name: '确认驳回' }))

    expect(await screen.findByText('申请已驳回')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[1]).not.toBe(keys[0])
  })

  it('refreshes a stale task version and requires a new confirmed intent', async () => {
    const user = userEvent.setup()
    const bodies: unknown[] = []
    const keys: Array<string | null> = []
    let taskRequestCount = 0
    let approvalCount = 0
    let approved = false
    server.use(
      http.get(taskUrl, () => {
        taskRequestCount += 1
        if (approved) {
          return HttpResponse.json(terminalTask('APPROVED', 4))
        }
        return HttpResponse.json(
          taskRequestCount === 1 ? taskDetail() : taskDetail({ version: 3 }),
        )
      }),
      http.post(approveUrl, async ({ request }) => {
        approvalCount += 1
        bodies.push(await request.json())
        keys.push(request.headers.get('Idempotency-Key'))
        if (approvalCount === 1) {
          return HttpResponse.json(
            backendError('CONCURRENT_MODIFICATION', 'Version is stale'),
            { status: 409 },
          )
        }
        approved = true
        return HttpResponse.json(
          decisionResponse('APPROVED', '同意采购'),
        )
      }),
      http.get(finalDecisionUrl, () =>
        HttpResponse.json(finalDecision('APPROVED', '同意采购')),
      ),
    )

    await renderPending()
    await approveWithComment(user, '同意采购')
    expect(await screen.findByText('审批任务版本已变化')).toBeInTheDocument()
    expect(screen.getByText(/当前服务端任务状态为 PENDING，版本为 3/)).toBeInTheDocument()

    await user.click(
      screen.getByRole('button', { name: '放弃本次决定意图' }),
    )
    await approveWithComment(user, '同意采购')

    expect(await screen.findByText('申请已批准')).toBeInTheDocument()
    expect(bodies).toEqual([
      { approvalTaskVersion: 2, comment: '同意采购' },
      { approvalTaskVersion: 3, comment: '同意采购' },
    ])
    expect(keys[1]).not.toBe(keys[0])
  })

  it('refreshes a business conflict into a read-only server decision', async () => {
    const user = userEvent.setup()
    let changedByOtherRequest = false
    server.use(
      http.get(taskUrl, () =>
        HttpResponse.json(
          changedByOtherRequest ? terminalTask('REJECTED') : taskDetail(),
        ),
      ),
      http.post(approveUrl, () => {
        changedByOtherRequest = true
        return HttpResponse.json(
          backendError('BUSINESS_CONFLICT', 'Task is no longer pending'),
          { status: 409 },
        )
      }),
      http.get(finalDecisionUrl, () =>
        HttpResponse.json(finalDecision('REJECTED', '其他审批请求已驳回')),
      ),
    )

    await renderPending()
    await approveWithComment(user)
    expect(await screen.findByText('审批任务状态已变化')).toBeInTheDocument()
    expect(screen.getByText(/当前服务端任务状态为 REJECTED/)).toBeInTheDocument()

    await user.click(
      screen.getByRole('button', { name: '放弃本次决定意图' }),
    )
    expect(
      (await screen.findAllByText('已驳回（REJECTED）')).length,
    ).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('其他审批请求已驳回')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '批准申请' })).not.toBeInTheDocument()
  })

  it('enforces the 2000-character comment limit before sending a request', async () => {
    const user = userEvent.setup()
    let requestCount = 0
    server.use(
      http.get(taskUrl, () => HttpResponse.json(taskDetail())),
      http.post(approveUrl, () => {
        requestCount += 1
        return HttpResponse.json(decisionResponse('APPROVED', null))
      }),
    )

    await renderPending()
    await user.click(screen.getByRole('button', { name: '批准申请' }))
    fireEvent.change(screen.getByLabelText('批准意见（可选）'), {
      target: { value: 'a'.repeat(2001) },
    })
    await user.click(screen.getByRole('button', { name: '确认批准' }))

    expect(
      await screen.findByText('审批意见不能超过 2000 个字符'),
    ).toBeInTheDocument()
    expect(requestCount).toBe(0)
  })
})
