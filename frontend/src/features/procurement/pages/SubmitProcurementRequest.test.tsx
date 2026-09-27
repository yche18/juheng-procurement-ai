import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

const requestId = '10000000-0000-0000-0000-000000000001'
const approvalTaskId = '30000000-0000-0000-0000-000000000001'
const detailUrl = `${API_ORIGIN}/api/procurement-requests/${requestId}`
const submitUrl = `${detailUrl}/submit`
const auditUrl = `${detailUrl}/audit-events`

function requesterStore() {
  const store = createAppStore()
  store.dispatch(
    sessionAuthenticated({
      userId: 'demo-requester',
      roles: ['REQUESTER'],
    }),
  )
  return store
}

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: requestId,
    businessNumber: 'PR-20260927-1001',
    creatorId: 'demo-requester',
    title: '研发电脑采购',
    purpose: '补充开发设备',
    department: '研发部',
    expectedDeliveryDate: '2026-10-10',
    currency: 'CNY',
    estimatedTotal: 18000,
    status: 'DRAFT',
    version: 3,
    createdAt: '2026-09-27T01:00:00Z',
    updatedAt: '2026-09-27T03:00:00Z',
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
    ...overrides,
  }
}

function submittedResponse(overrides: Record<string, unknown> = {}) {
  return {
    requestId,
    requestStatus: 'SUBMITTED',
    requestVersion: 4,
    approvalTaskId,
    approvalTaskStatus: 'PENDING',
    ...overrides,
  }
}

function backendError(code: string, message: string) {
  return {
    code,
    message,
    path: `/api/procurement-requests/${requestId}/submit`,
    fieldErrors: [],
  }
}

async function renderDraft() {
  const result = renderApplication({
    initialEntries: [`/requester/requests/${requestId}`],
    store: requesterStore(),
  })
  expect(
    await screen.findByRole('button', { name: '提交审批' }),
  ).toBeEnabled()
  return result
}

async function openAndConfirm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: '提交审批' }))
  expect(screen.getByText('提交后核心字段不可普通修改')).toBeInTheDocument()
  expect(screen.getAllByText('PR-20260927-1001').length).toBeGreaterThan(0)
  expect(screen.getAllByText(/18,000\.00/).length).toBeGreaterThan(0)
  await user.click(screen.getByRole('button', { name: /确认提交/ }))
}

describe('SubmitProcurementRequest', () => {
  it('submits the current version, shows the created task, and refreshes the detail', async () => {
    const user = userEvent.setup()
    let submitted = false
    let detailRequestCount = 0
    let auditRequestCount = 0
    let requestBody: unknown
    let idempotencyKey: string | null = null
    server.use(
      http.get(detailUrl, () => {
        detailRequestCount += 1
        return HttpResponse.json(
          submitted ? detail({ status: 'SUBMITTED', version: 4 }) : detail(),
        )
      }),
      http.post(submitUrl, async ({ request }) => {
        requestBody = await request.json()
        idempotencyKey = request.headers.get('Idempotency-Key')
        submitted = true
        return HttpResponse.json(submittedResponse())
      }),
      http.get(auditUrl, () => {
        auditRequestCount += 1
        return HttpResponse.json({ procurementRequestId: requestId, events: [] })
      }),
    )

    await renderDraft()
    await waitFor(() => expect(auditRequestCount).toBe(1))
    await openAndConfirm(user)

    expect(await screen.findByText('审批任务已创建')).toBeInTheDocument()
    expect(screen.getByText(approvalTaskId)).toBeInTheDocument()
    expect(screen.getByText('PENDING')).toBeInTheDocument()
    expect(requestBody).toEqual({ version: 3 })
    expect(idempotencyKey).toMatch(/^.{1,64}$/)
    await waitFor(() => expect(detailRequestCount).toBeGreaterThanOrEqual(2))
    await waitFor(() => expect(auditRequestCount).toBeGreaterThanOrEqual(2))

    await user.click(screen.getByRole('button', { name: /关.*闭/ }))
    expect(await screen.findByText('待审批（SUBMITTED）')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '提交审批' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: '编辑草稿' }),
    ).not.toBeInTheDocument()
    expect(window.sessionStorage).toHaveLength(0)
  })

  it('restores the original key after an uncertain network result and remount', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    let submissionCount = 0
    let submitted = false
    server.use(
      http.get(detailUrl, () =>
        HttpResponse.json(
          submitted ? detail({ status: 'SUBMITTED', version: 4 }) : detail(),
        ),
      ),
      http.post(submitUrl, ({ request }) => {
        submissionCount += 1
        keys.push(request.headers.get('Idempotency-Key'))
        if (submissionCount === 1) {
          return HttpResponse.error()
        }
        submitted = true
        return HttpResponse.json(submittedResponse())
      }),
    )

    const firstView = await renderDraft()
    await openAndConfirm(user)
    expect(await screen.findByText('无法确认提交结果')).toBeInTheDocument()
    expect(screen.getByText(/保留原版本和幂等键/)).toBeInTheDocument()
    firstView.unmount()

    await renderDraft()
    await openAndConfirm(user)

    expect(await screen.findByText('审批任务已创建')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBeTruthy()
    expect(keys[1]).toBe(keys[0])
    expect(window.sessionStorage).toHaveLength(0)
  })

  it('reuses the same key while an identical submission is still in progress', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    let submissionCount = 0
    server.use(
      http.get(detailUrl, () => HttpResponse.json(detail())),
      http.post(submitUrl, ({ request }) => {
        submissionCount += 1
        keys.push(request.headers.get('Idempotency-Key'))
        if (submissionCount === 1) {
          return HttpResponse.json(
            backendError(
              'IDEMPOTENCY_IN_PROGRESS',
              'An identical request is still being processed',
            ),
            { status: 409 },
          )
        }
        return HttpResponse.json(submittedResponse())
      }),
    )

    await renderDraft()
    await openAndConfirm(user)
    expect(
      await screen.findByText('相同提交正在处理中'),
    ).toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: /原幂等键重试/ }),
    )

    expect(await screen.findByText('审批任务已创建')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[1]).toBe(keys[0])
  })

  it('keeps the draft and original key when approval routing fails', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    server.use(
      http.get(detailUrl, () => HttpResponse.json(detail())),
      http.post(submitUrl, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        return HttpResponse.json(
          backendError(
            'APPROVAL_ROUTING_FAILED',
            'No unique valid approver could be resolved',
          ),
          { status: 409 },
        )
      }),
    )

    await renderDraft()
    await openAndConfirm(user)
    expect(await screen.findByText('审批路由失败')).toBeInTheDocument()
    expect(screen.getByText(/申请仍为草稿/)).toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: /原幂等键重试/ }),
    )

    await waitFor(() => expect(keys).toHaveLength(2))
    expect(keys[1]).toBe(keys[0])
    expect(screen.getByRole('button', { name: '提交审批' })).toBeEnabled()
  })

  it('stops on idempotency conflict and only creates a new key after explicit abandonment', async () => {
    const user = userEvent.setup()
    const keys: Array<string | null> = []
    server.use(
      http.get(detailUrl, () => HttpResponse.json(detail())),
      http.post(submitUrl, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        return HttpResponse.json(
          backendError(
            'IDEMPOTENCY_CONFLICT',
            'Idempotency key conflicts with a different request',
          ),
          { status: 409 },
        )
      }),
    )

    await renderDraft()
    await openAndConfirm(user)
    expect(
      await screen.findByText('幂等键与既有请求冲突'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Close' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /确认提交/ })).toBeDisabled()
    expect(keys).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: /放弃本次提交/ }))
    await openAndConfirm(user)
    await waitFor(() => expect(keys).toHaveLength(2))
    expect(keys[1]).not.toBe(keys[0])
  })

  it('refreshes a stale version and requires confirmation of a new intent', async () => {
    const user = userEvent.setup()
    const bodies: unknown[] = []
    const keys: Array<string | null> = []
    let detailRequestCount = 0
    let submissionCount = 0
    server.use(
      http.get(detailUrl, () => {
        detailRequestCount += 1
        return HttpResponse.json(
          detailRequestCount === 1 ? detail() : detail({ version: 4 }),
        )
      }),
      http.post(submitUrl, async ({ request }) => {
        submissionCount += 1
        bodies.push(await request.json())
        keys.push(request.headers.get('Idempotency-Key'))
        if (submissionCount === 1) {
          return HttpResponse.json(
            backendError('CONCURRENT_MODIFICATION', 'Version is stale'),
            { status: 409 },
          )
        }
        return HttpResponse.json(
          submittedResponse({ requestVersion: 5 }),
        )
      }),
    )

    await renderDraft()
    await openAndConfirm(user)
    expect(await screen.findByText('申请版本已变化')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Close' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText(/当前服务端状态为 DRAFT，版本为 4/)).toBeInTheDocument()
    expect(detailRequestCount).toBe(2)

    await user.click(
      screen.getByRole('button', { name: /基于最新版本重新确认/ }),
    )
    await openAndConfirm(user)

    expect(await screen.findByText('审批任务已创建')).toBeInTheDocument()
    expect(bodies).toEqual([{ version: 3 }, { version: 4 }])
    expect(keys[1]).not.toBe(keys[0])
  })

  it('refreshes a business conflict and leaves the latest request read-only', async () => {
    const user = userEvent.setup()
    let detailRequestCount = 0
    server.use(
      http.get(detailUrl, () => {
        detailRequestCount += 1
        return HttpResponse.json(
          detailRequestCount === 1
            ? detail()
            : detail({ status: 'SUBMITTED', version: 4 }),
        )
      }),
      http.post(submitUrl, () =>
        HttpResponse.json(
          backendError('BUSINESS_CONFLICT', 'Request is not a draft'),
          { status: 409 },
        ),
      ),
    )

    await renderDraft()
    await openAndConfirm(user)
    expect(await screen.findByText('申请状态已变化')).toBeInTheDocument()
    expect(
      screen.getByText(/当前服务端状态为 SUBMITTED，版本为 4/),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /取.*消/ }))
    expect(await screen.findByText('待审批（SUBMITTED）')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '提交审批' }),
    ).not.toBeInTheDocument()
    expect(window.sessionStorage).toHaveLength(0)
  })

  it('prevents a duplicate click while the request is pending', async () => {
    const user = userEvent.setup()
    let requestCount = 0
    let releaseRequest: () => void = () => undefined
    const requestGate = new Promise<void>((resolve) => {
      releaseRequest = resolve
    })
    server.use(
      http.get(detailUrl, () => HttpResponse.json(detail())),
      http.post(submitUrl, async () => {
        requestCount += 1
        await requestGate
        return HttpResponse.json(submittedResponse())
      }),
    )

    await renderDraft()
    await user.click(screen.getByRole('button', { name: '提交审批' }))
    const confirmButton = screen.getByRole('button', { name: '确认提交' })
    await user.click(confirmButton)

    await waitFor(() => expect(confirmButton).toBeDisabled())
    await user.click(confirmButton)
    expect(requestCount).toBe(1)

    releaseRequest()
    expect(await screen.findByText('审批任务已创建')).toBeInTheDocument()
  })
})
