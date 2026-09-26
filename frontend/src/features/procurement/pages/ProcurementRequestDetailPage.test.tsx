import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

const requestId = '10000000-0000-0000-0000-000000000001'

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
    businessNumber: 'PR-20260926-1001',
    creatorId: 'demo-requester',
    title: '研发电脑采购',
    purpose: '补充开发设备',
    department: '研发部',
    expectedDeliveryDate: '2026-10-10',
    currency: 'CNY',
    estimatedTotal: 18000,
    status: 'APPROVED',
    version: 2,
    createdAt: '2026-09-26T01:00:00Z',
    updatedAt: '2026-09-26T03:00:00Z',
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

function finalDecision(overrides: Record<string, unknown> = {}) {
  return {
    procurementRequestId: requestId,
    approvalTaskId: '30000000-0000-0000-0000-000000000001',
    decisionId: '40000000-0000-0000-0000-000000000001',
    decision: 'APPROVED',
    actorId: 'demo-approver',
    decidedAt: '2026-09-26T03:00:00Z',
    comment: '预算和需求均已确认',
    ...overrides,
  }
}

describe('ProcurementRequestDetailPage', () => {
  it('renders the complete server detail and persisted final decision', async () => {
    let decisionRequestCount = 0
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json(detail()),
      ),
      http.get(
        `${API_ORIGIN}/api/procurement-requests/${requestId}/approval-decision`,
        () => {
          decisionRequestCount += 1
          return HttpResponse.json(finalDecision())
        },
      ),
    )

    renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })

    expect(await screen.findByText('PR-20260926-1001')).toBeInTheDocument()
    expect(screen.getByText('补充开发设备')).toBeInTheDocument()
    expect(screen.getByText('研发部')).toBeInTheDocument()
    expect(screen.getByText('CNY')).toBeInTheDocument()
    expect(screen.getByText('开发笔记本')).toBeInTheDocument()
    expect(screen.getByText('笔记本电脑（LAPTOP）')).toBeInTheDocument()
    expect(screen.getAllByText(/18,000\.00/).length).toBeGreaterThan(0)
    expect(
      await screen.findByText('预算和需求均已确认'),
    ).toBeInTheDocument()
    expect(screen.getByText('demo-approver')).toBeInTheDocument()
    expect(screen.getAllByText('已批准（APPROVED）')).toHaveLength(2)
    expect(
      screen.queryByRole('link', { name: '编辑草稿' }),
    ).not.toBeInTheDocument()
    expect(decisionRequestCount).toBe(1)
  })

  it('does not request a final decision for a draft', async () => {
    let decisionRequestCount = 0
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json(detail({ status: 'DRAFT' })),
      ),
      http.get(
        `${API_ORIGIN}/api/procurement-requests/${requestId}/approval-decision`,
        () => {
          decisionRequestCount += 1
          return HttpResponse.json(finalDecision())
        },
      ),
    )

    renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })

    expect(
      await screen.findByText('当前状态尚无最终审批结果。'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '编辑草稿' })).toHaveAttribute(
      'href',
      `/requester/requests/${requestId}/edit`,
    )
    expect(decisionRequestCount).toBe(0)
  })

  it('uses the same safe state for a missing or inaccessible request', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json(
          {
            code: 'RESOURCE_NOT_FOUND',
            message: 'Resource was not found',
            path: `/api/procurement-requests/${requestId}`,
            fieldErrors: [],
          },
          { status: 404 },
        ),
      ),
    )

    renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })

    expect(
      await screen.findByText('申请不存在或不可访问'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/属于其他用户/)).not.toBeInTheDocument()
  })

  it('keeps an approved request visible when its final decision is unavailable', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json(detail()),
      ),
      http.get(
        `${API_ORIGIN}/api/procurement-requests/${requestId}/approval-decision`,
        () =>
          HttpResponse.json(
            {
              code: 'RESOURCE_NOT_FOUND',
              message: 'Resource was not found',
              path: `/api/procurement-requests/${requestId}/approval-decision`,
              fieldErrors: [],
            },
            { status: 404 },
          ),
      ),
    )

    renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })

    expect(await screen.findByText('PR-20260926-1001')).toBeInTheDocument()
    expect(
      await screen.findByText('最终审批结果尚不存在或不可访问。'),
    ).toBeInTheDocument()
  })

  it('does not infer a decision from an unknown request status', async () => {
    let decisionRequestCount = 0
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json(detail({ status: 'ARCHIVED' })),
      ),
      http.get(
        `${API_ORIGIN}/api/procurement-requests/${requestId}/approval-decision`,
        () => {
          decisionRequestCount += 1
          return HttpResponse.json(finalDecision())
        },
      ),
    )

    renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })

    expect(await screen.findByText('未知状态（ARCHIVED）')).toBeInTheDocument()
    expect(screen.getByText('申请状态不是已知枚举')).toBeInTheDocument()
    expect(decisionRequestCount).toBe(0)
  })

  it('rejects malformed detail and final-decision responses', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json({ id: requestId, title: '缺少必要字段' }),
      ),
    )
    const malformedDetailView = renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })
    expect(
      await screen.findByText('服务响应与申请详情 Contract 不一致'),
    ).toBeInTheDocument()
    malformedDetailView.unmount()

    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests/${requestId}`, () =>
        HttpResponse.json(detail()),
      ),
      http.get(
        `${API_ORIGIN}/api/procurement-requests/${requestId}/approval-decision`,
        () => HttpResponse.json({ decision: 'APPROVED' }),
      ),
    )
    renderApplication({
      initialEntries: [`/requester/requests/${requestId}`],
      store: requesterStore(),
    })
    expect(
      await screen.findByText('服务响应与最终决定 Contract 不一致'),
    ).toBeInTheDocument()
  })
})
