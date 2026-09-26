import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

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

function summary(overrides: Record<string, unknown> = {}) {
  return {
    id: '10000000-0000-0000-0000-000000000001',
    businessNumber: 'PR-20260926-1001',
    title: '研发电脑采购',
    department: '研发部',
    expectedDeliveryDate: '2026-10-10',
    currency: 'CNY',
    estimatedTotal: 18000,
    status: 'DRAFT',
    version: 0,
    createdAt: '2026-09-26T01:00:00Z',
    updatedAt: '2026-09-26T02:00:00Z',
    ...overrides,
  }
}

function pageResponse(content: unknown[], page = 0, size = 20) {
  return {
    content,
    page,
    size,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
  }
}

describe('ProcurementRequestListPage', () => {
  it('maps URL pagination to the server and keeps unknown status visible', async () => {
    let observedQuery = ''
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests`, ({ request }) => {
        observedQuery = new URL(request.url).searchParams.toString()
        return HttpResponse.json(
          pageResponse([summary({ status: 'PAUSED_BY_SYSTEM' })], 1, 10),
        )
      }),
    )

    renderApplication({
      initialEntries: ['/requester/requests?page=2&size=10'],
      store: requesterStore(),
    })

    expect(await screen.findByText('PR-20260926-1001')).toBeInTheDocument()
    expect(screen.getByText('研发电脑采购')).toBeInTheDocument()
    expect(screen.getByText('研发部')).toBeInTheDocument()
    expect(screen.getByText('2026-10-10')).toBeInTheDocument()
    expect(screen.getByText(/18,000\.00/)).toBeInTheDocument()
    expect(screen.getByText('未知状态（PAUSED_BY_SYSTEM）')).toBeInTheDocument()
    expect(observedQuery).toContain('page=1')
    expect(observedQuery).toContain('size=10')
    expect(observedQuery).not.toContain('creatorId')
  })

  it('stores the status filter in the URL and distinguishes empty states', async () => {
    const user = userEvent.setup()
    let observedStatus: string | null = null
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests`, ({ request }) => {
        observedStatus = new URL(request.url).searchParams.get('status')
        return HttpResponse.json(pageResponse([]))
      }),
    )

    const { router } = renderApplication({
      initialEntries: ['/requester/requests'],
      store: requesterStore(),
    })

    expect(
      await screen.findByText('你还没有创建采购申请。'),
    ).toBeInTheDocument()
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '申请状态' }))
    await user.click(await screen.findByText('草稿（DRAFT）'))

    expect(
      await screen.findByText('当前状态筛选下没有申请。'),
    ).toBeInTheDocument()
    await waitFor(() => expect(observedStatus).toBe('DRAFT'))
    expect(router.state.location.search).toBe('?page=1&size=20&status=DRAFT')
  })

  it('shows request and contract failures without treating them as empty data', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests`, () =>
        HttpResponse.json(
          {
            code: 'INTERNAL_ERROR',
            message: '申请列表暂时不可用',
            path: '/api/procurement-requests',
            fieldErrors: [],
          },
          { status: 500 },
        ),
      ),
    )

    const failedView = renderApplication({
      initialEntries: ['/requester/requests'],
      store: requesterStore(),
    })
    expect(await screen.findByText('申请列表加载失败')).toBeInTheDocument()
    expect(screen.getByText('申请列表暂时不可用')).toBeInTheDocument()
    failedView.unmount()

    server.use(
      http.get(`${API_ORIGIN}/api/procurement-requests`, () =>
        HttpResponse.json({ content: [], page: '0' }),
      ),
    )
    renderApplication({
      initialEntries: ['/requester/requests'],
      store: requesterStore(),
    })
    expect(
      await screen.findByText('服务响应与列表 Contract 不一致'),
    ).toBeInTheDocument()
    expect(screen.queryByText('你还没有创建采购申请。')).not.toBeInTheDocument()
  })

  it('requires the REQUESTER role before loading the list', async () => {
    const store = createAppStore()
    store.dispatch(
      sessionAuthenticated({
        userId: 'demo-approver',
        roles: ['APPROVER'],
      }),
    )

    renderApplication({
      initialEntries: ['/requester/requests'],
      store,
    })

    expect(await screen.findByText('无权访问')).toBeInTheDocument()
  })
})
