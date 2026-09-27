import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

const taskId = '30000000-0000-0000-0000-000000000001'

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
      id: '10000000-0000-0000-0000-000000000001',
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

describe('ApprovalTaskDetailPage', () => {
  it('renders task metadata and the complete procurement request as read-only', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks/${taskId}`, () =>
        HttpResponse.json(taskDetail()),
      ),
    )

    renderApplication({
      initialEntries: [`/approver/tasks/${taskId}`],
      store: approverStore(),
    })

    expect(
      await screen.findByRole('heading', { name: '审批任务详情' }),
    ).toBeInTheDocument()
    expect(screen.getByText(taskId)).toBeInTheDocument()
    expect(screen.getByText('待审批（PENDING）')).toBeInTheDocument()
    expect(screen.getAllByText('demo-approver').length).toBeGreaterThan(1)
    expect(screen.getByText('PR-20260927-1001')).toBeInTheDocument()
    expect(screen.getByText('研发电脑采购')).toBeInTheDocument()
    expect(screen.getByText('demo-requester')).toBeInTheDocument()
    expect(screen.getByText('补充开发设备')).toBeInTheDocument()
    expect(screen.getByText('开发笔记本')).toBeInTheDocument()
    expect(screen.getByText('笔记本电脑（LAPTOP）')).toBeInTheDocument()
    expect(screen.getAllByText(/18,000\.00/).length).toBeGreaterThan(0)
    expect(screen.getByText('采购申请（只读）')).toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: '编辑草稿' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /批准|驳回/ }),
    ).not.toBeInTheDocument()
  })

  it('uses one safe not-found state for a missing or unassigned task', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks/${taskId}`, () =>
        HttpResponse.json(
          {
            code: 'RESOURCE_NOT_FOUND',
            message: 'Resource was not found',
            path: `/api/approval-tasks/${taskId}`,
            fieldErrors: [],
          },
          { status: 404 },
        ),
      ),
    )

    renderApplication({
      initialEntries: [`/approver/tasks/${taskId}`],
      store: approverStore(),
    })

    expect(
      await screen.findByText('审批任务不存在或不可访问'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/其他审批人/)).not.toBeInTheDocument()
    expect(screen.queryByText('研发电脑采购')).not.toBeInTheDocument()
  })

  it('rejects a malformed detail response instead of guessing missing fields', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks/${taskId}`, () =>
        HttpResponse.json({
          id: taskId,
          status: 'PENDING',
          procurementRequest: { title: '缺少必要字段' },
        }),
      ),
    )

    renderApplication({
      initialEntries: [`/approver/tasks/${taskId}`],
      store: approverStore(),
    })

    expect(
      await screen.findByText('服务响应与审批任务详情 Contract 不一致'),
    ).toBeInTheDocument()
    expect(screen.queryByText('缺少必要字段')).not.toBeInTheDocument()
  })

  it('preserves unknown task and request statuses as visible server codes', async () => {
    const response = taskDetail({
      status: 'WAITING_REVIEW',
      procurementRequest: {
        ...taskDetail().procurementRequest,
        status: 'PAUSED_BY_SYSTEM',
      },
    })
    server.use(
      http.get(`${API_ORIGIN}/api/approval-tasks/${taskId}`, () =>
        HttpResponse.json(response),
      ),
    )

    renderApplication({
      initialEntries: [`/approver/tasks/${taskId}`],
      store: approverStore(),
    })

    expect(
      await screen.findByText('服务端返回了未知审批任务状态'),
    ).toBeInTheDocument()
    expect(screen.getByText('未知状态（WAITING_REVIEW）')).toBeInTheDocument()
    expect(screen.getByText('服务端返回了未知申请状态')).toBeInTheDocument()
    expect(screen.getByText('未知状态（PAUSED_BY_SYSTEM）')).toBeInTheDocument()
  })
})
