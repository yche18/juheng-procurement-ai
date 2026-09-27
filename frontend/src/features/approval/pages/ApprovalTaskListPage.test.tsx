import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

function storeWithRoles(roles: string[]) {
  const store = createAppStore()
  store.dispatch(
    sessionAuthenticated({
      userId: roles.includes('APPROVER')
        ? 'demo-approver'
        : 'demo-requester',
      roles,
    }),
  )
  return store
}

function taskSummary(overrides: Record<string, unknown> = {}) {
  return {
    id: '30000000-0000-0000-0000-000000000001',
    status: 'PENDING',
    version: 7,
    createdAt: '2026-09-27T01:00:00Z',
    updatedAt: '2026-09-27T02:00:00Z',
    procurementRequest: {
      id: '10000000-0000-0000-0000-000000000001',
      businessNumber: 'PR-20260927-1001',
      creatorId: 'demo-requester',
      title: '研发电脑采购',
      department: '研发部',
      expectedDeliveryDate: '2026-10-10',
      currency: 'CNY',
      estimatedTotal: 18000,
      status: 'SUBMITTED',
      version: 4,
    },
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

describe('ApprovalTaskListPage', () => {
  it('maps URL pagination without sending assigneeId and preserves unknown task status', async () => {
    let observedQuery = ''
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks`, ({ request }) => {
        observedQuery = new URL(request.url).searchParams.toString()
        return HttpResponse.json(
          pageResponse(
            [taskSummary({ status: 'WAITING_REVIEW' })],
            1,
            10,
          ),
        )
      }),
    )

    renderApplication({
      initialEntries: [
        '/approver/tasks?page=2&size=10&status=NOT_A_TASK_STATUS',
      ],
      store: storeWithRoles(['APPROVER']),
    })

    expect(await screen.findByText('PR-20260927-1001')).toBeInTheDocument()
    expect(screen.getByText('研发电脑采购')).toBeInTheDocument()
    expect(screen.getByText('demo-requester')).toBeInTheDocument()
    expect(screen.getByText('研发部')).toBeInTheDocument()
    expect(screen.getByText('2026-10-10')).toBeInTheDocument()
    expect(screen.getByText(/18,000\.00/)).toBeInTheDocument()
    expect(screen.getByText('未知状态（WAITING_REVIEW）')).toBeInTheDocument()
    expect(
      screen.getByText('URL 中包含未知任务状态筛选'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'PR-20260927-1001' }),
    ).toHaveAttribute(
      'href',
      '/approver/tasks/30000000-0000-0000-0000-000000000001',
    )
    expect(observedQuery).toContain('page=1')
    expect(observedQuery).toContain('size=10')
    expect(observedQuery).not.toContain('status')
    expect(observedQuery).not.toContain('assigneeId')
  })

  it('stores a valid status filter in the URL and distinguishes empty states', async () => {
    const user = userEvent.setup()
    let observedStatus: string | null = null
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks`, ({ request }) => {
        observedStatus = new URL(request.url).searchParams.get('status')
        return HttpResponse.json(pageResponse([]))
      }),
    )

    const { router } = renderApplication({
      initialEntries: ['/approver/tasks'],
      store: storeWithRoles(['APPROVER']),
    })

    expect(
      await screen.findByText('当前没有分配给你的审批任务。'),
    ).toBeInTheDocument()
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '任务状态' }))
    await user.click(await screen.findByText('已批准（APPROVED）'))

    expect(
      await screen.findByText('当前状态筛选下没有审批任务。'),
    ).toBeInTheDocument()
    await waitFor(() => expect(observedStatus).toBe('APPROVED'))
    expect(router.state.location.search).toBe(
      '?page=1&size=20&status=APPROVED',
    )
  })

  it('shows request and contract failures without treating them as empty tasks', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks`, () =>
        HttpResponse.json(
          {
            code: 'INTERNAL_ERROR',
            message: '审批任务列表暂时不可用',
            path: '/api/approval-tasks',
            fieldErrors: [],
          },
          { status: 500 },
        ),
      ),
    )

    const failedView = renderApplication({
      initialEntries: ['/approver/tasks'],
      store: storeWithRoles(['APPROVER']),
    })
    expect(
      await screen.findByText('审批任务列表加载失败'),
    ).toBeInTheDocument()
    expect(screen.getByText('审批任务列表暂时不可用')).toBeInTheDocument()
    failedView.unmount()

    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks`, () =>
        HttpResponse.json({ content: [], page: '0' }),
      ),
    )
    renderApplication({
      initialEntries: ['/approver/tasks'],
      store: storeWithRoles(['APPROVER']),
    })
    expect(
      await screen.findByText('服务响应与审批任务列表 Contract 不一致'),
    ).toBeInTheDocument()
    expect(
      screen.queryByText('当前没有分配给你的审批任务。'),
    ).not.toBeInTheDocument()
  })

  it('requires the APPROVER role before loading assigned tasks', async () => {
    let requestCount = 0
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks`, () => {
        requestCount += 1
        return HttpResponse.json(pageResponse([]))
      }),
    )

    renderApplication({
      initialEntries: ['/approver/tasks'],
      store: storeWithRoles(['REQUESTER']),
    })

    expect(await screen.findByText('无权访问')).toBeInTheDocument()
    expect(requestCount).toBe(0)
  })
})
