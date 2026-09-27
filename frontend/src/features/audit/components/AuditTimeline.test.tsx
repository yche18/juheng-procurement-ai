import { screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'
import { AuditTimeline } from './AuditTimeline'

const requestId = '10000000-0000-0000-0000-000000000001'
const auditUrl =
  `${API_ORIGIN}/api/procurement-requests/${requestId}/audit-events`

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

function renderAuditTimeline() {
  return renderApplication({
    initialEntries: ['/audit'],
    routes: [
      {
        path: '/audit',
        element: <AuditTimeline requestId={requestId} />,
      },
    ],
    store: requesterStore(),
  })
}

function auditEvent(overrides: Record<string, unknown> = {}) {
  return {
    id: '50000000-0000-0000-0000-000000000001',
    actorId: 'demo-requester',
    action: 'PROCUREMENT_REQUEST_CREATED',
    targetType: 'PROCUREMENT_REQUEST',
    targetId: requestId,
    timestamp: '2026-09-27T01:00:00Z',
    result: 'SUCCESS',
    requestIdentifier: 'request-create-001',
    ...overrides,
  }
}

describe('AuditTimeline', () => {
  it('keeps the exact server order and safely displays known and unknown codes', async () => {
    server.use(
      http.get(auditUrl, () =>
        HttpResponse.json({
          procurementRequestId: requestId,
          events: [
            auditEvent({
              id: '50000000-0000-0000-0000-000000000002',
              action: 'CUSTOM_AUDIT_ACTION',
              targetType: 'CUSTOM_TARGET',
              result: 'CUSTOM_RESULT',
              timestamp: '2026-09-27T03:00:00Z',
              requestIdentifier: 'server-first-event',
            }),
            auditEvent({
              timestamp: '2026-09-27T01:00:00Z',
              requestIdentifier: 'server-second-event',
            }),
          ],
        }),
      ),
    )

    const view = renderAuditTimeline()

    expect(
      await screen.findByText('未知动作（CUSTOM_AUDIT_ACTION）'),
    ).toBeInTheDocument()
    expect(screen.getByText('未知目标类型（CUSTOM_TARGET）')).toBeInTheDocument()
    expect(screen.getByText('未知结果（CUSTOM_RESULT）')).toBeInTheDocument()
    expect(screen.getByText('创建采购申请（PROCUREMENT_REQUEST_CREATED）'))
      .toBeInTheDocument()
    expect(screen.getAllByText('demo-requester')).toHaveLength(2)
    expect(screen.getByText('server-first-event')).toBeInTheDocument()
    expect(screen.getByText('server-second-event')).toBeInTheDocument()

    const content = view.container.textContent ?? ''
    expect(content.indexOf('CUSTOM_AUDIT_ACTION')).toBeLessThan(
      content.indexOf('PROCUREMENT_REQUEST_CREATED'),
    )
    expect(
      screen.queryByRole('button', { name: /创建|修改|删除审计/ }),
    ).not.toBeInTheDocument()
  })

  it('shows an initial loading state and then an empty read-only trail', async () => {
    server.use(
      http.get(auditUrl, async () => {
        await delay(100)
        return HttpResponse.json({
          procurementRequestId: requestId,
          events: [],
        })
      }),
    )

    const view = renderAuditTimeline()

    expect(view.container.querySelector('.ant-skeleton')).toBeInTheDocument()
    expect(await screen.findByText('暂无审计事件。')).toBeInTheDocument()
    expect(screen.getByText('共 0 条服务端事件')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '刷新审计轨迹' })).toBeEnabled()
  })

  it('uses a non-enumerating error state for an inaccessible trail', async () => {
    server.use(
      http.get(auditUrl, () =>
        HttpResponse.json(
          {
            code: 'RESOURCE_NOT_FOUND',
            message: 'Resource was not found',
            path: `/api/procurement-requests/${requestId}/audit-events`,
            fieldErrors: [],
          },
          { status: 404 },
        ),
      ),
    )

    renderAuditTimeline()

    expect(
      await screen.findByText('审计轨迹不存在或不可访问'),
    ).toBeInTheDocument()
    expect(screen.getByText('服务端不会区分申请不存在与当前用户无权访问。'))
      .toBeInTheDocument()
    expect(screen.queryByText('demo-requester')).not.toBeInTheDocument()
  })

  it('rejects malformed or incorrectly associated audit responses', async () => {
    server.use(
      http.get(auditUrl, () =>
        HttpResponse.json({ procurementRequestId: requestId, events: [{}] }),
      ),
    )
    const malformedView = renderAuditTimeline()
    expect(
      await screen.findByText('服务响应与审计轨迹 Contract 不一致'),
    ).toBeInTheDocument()
    malformedView.unmount()

    server.use(
      http.get(auditUrl, () =>
        HttpResponse.json({
          procurementRequestId: '10000000-0000-0000-0000-000000000099',
          events: [auditEvent()],
        }),
      ),
    )
    renderAuditTimeline()
    expect(
      await screen.findByText('审计轨迹关联的申请不一致'),
    ).toBeInTheDocument()
    expect(screen.queryByText('request-create-001')).not.toBeInTheDocument()
  })
})
