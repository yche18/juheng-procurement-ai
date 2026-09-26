import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'

const requestId = '10000000-0000-0000-0000-000000000001'
const detailUrl = `${API_ORIGIN}/api/procurement-requests/${requestId}`

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
    status: 'DRAFT',
    version: 3,
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

function backendError(
  code: string,
  message: string,
  fieldErrors: Array<{ field: string; code: string; message: string }> = [],
) {
  return {
    code,
    message,
    path: `/api/procurement-requests/${requestId}`,
    fieldErrors,
  }
}

async function renderEditor() {
  const result = renderApplication({
    initialEntries: [`/requester/requests/${requestId}/edit`],
    store: requesterStore(),
  })
  expect(
    await screen.findByRole('heading', { name: '编辑采购申请草稿' }),
  ).toBeInTheDocument()
  return result
}

describe('EditProcurementRequestPage', () => {
  it('loads the full snapshot, puts only editable fields plus version, and shows server totals', async () => {
    const user = userEvent.setup()
    let requestBody: unknown
    let detailRequestCount = 0
    server.use(
      http.get(detailUrl, () => {
        detailRequestCount += 1
        return HttpResponse.json(detail())
      }),
      http.put(detailUrl, async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json(
          detail({
            title: '研发设备采购（更新）',
            estimatedTotal: 20000,
            version: 4,
            updatedAt: '2026-09-26T04:00:00Z',
            items: [
              {
                ...detail().items[0],
                estimatedUnitPrice: 10000,
                estimatedLineTotal: 20000,
              },
            ],
          }),
        )
      }),
    )

    await renderEditor()
    expect(screen.getByLabelText('标题')).toHaveValue('研发电脑采购')
    expect(screen.getByLabelText('申请部门')).toHaveValue('研发部')
    expect(screen.getByLabelText('采购目的')).toHaveValue('补充开发设备')
    expect(screen.getByLabelText('期望交付日期')).toHaveValue('2026-10-10')
    expect(screen.getByLabelText('名称')).toHaveValue('开发笔记本')
    expect(screen.getByLabelText('数量')).toHaveValue('2')
    expect(screen.getByLabelText('预计单价（CNY）')).toHaveValue('9000')

    await user.clear(screen.getByLabelText('标题'))
    await user.type(screen.getByLabelText('标题'), '研发设备采购（更新）')
    await user.clear(screen.getByLabelText('预计单价（CNY）'))
    await user.type(screen.getByLabelText('预计单价（CNY）'), '10000')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(await screen.findByText('采购申请草稿已保存')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getAllByText(/20,000\.00/).length).toBeGreaterThan(0)
    expect(requestBody).toEqual({
      version: 3,
      title: '研发设备采购（更新）',
      purpose: '补充开发设备',
      department: '研发部',
      expectedDeliveryDate: '2026-10-10',
      items: [
        {
          name: '开发笔记本',
          categoryCode: 'LAPTOP',
          specification: '32GB 内存',
          quantity: 2,
          unit: '台',
          estimatedUnitPrice: 10000,
        },
      ],
    })
    await waitFor(() => expect(detailRequestCount).toBeGreaterThanOrEqual(2))
  })

  it('maps backend field errors and keeps unlocated errors visible', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(detailUrl, () => HttpResponse.json(detail())),
      http.put(detailUrl, () =>
        HttpResponse.json(
          backendError('VALIDATION_FAILED', 'Request validation failed', [
            {
              field: 'items[0].quantity',
              code: 'DecimalMin',
              message: '数量不符合服务端规则',
            },
            {
              field: 'serverOnlyField',
              code: 'Invalid',
              message: '服务端返回了未知字段错误',
            },
          ]),
          { status: 400 },
        ),
      ),
    )

    await renderEditor()
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(await screen.findByText('数量不符合服务端规则')).toBeInTheDocument()
    expect(screen.getByText(/服务端返回了未知字段错误/)).toBeInTheDocument()
    expect(screen.getByLabelText('标题')).toHaveValue('研发电脑采购')
  })

  it('refuses direct editing when the server state is not DRAFT', async () => {
    server.use(
      http.get(detailUrl, () =>
        HttpResponse.json(detail({ status: 'SUBMITTED', version: 4 })),
      ),
    )

    renderApplication({
      initialEntries: [`/requester/requests/${requestId}/edit`],
      store: requesterStore(),
    })

    expect(await screen.findByText('当前申请不可编辑')).toBeInTheDocument()
    expect(screen.getByText('待审批（SUBMITTED）')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '保存草稿' })).not.toBeInTheDocument()
  })

  it('preserves local content on concurrent modification and reloads only after confirmation', async () => {
    const user = userEvent.setup()
    let detailRequestCount = 0
    server.use(
      http.get(detailUrl, () => {
        detailRequestCount += 1
        return HttpResponse.json(
          detailRequestCount === 1
            ? detail()
            : detail({ title: '服务端最新标题', version: 4 }),
        )
      }),
      http.put(detailUrl, () =>
        HttpResponse.json(
          backendError('CONCURRENT_MODIFICATION', 'Version is stale'),
          { status: 409 },
        ),
      ),
    )

    await renderEditor()
    await user.clear(screen.getByLabelText('标题'))
    await user.type(screen.getByLabelText('标题'), '我的未保存标题')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(await screen.findByText('草稿已被其他操作更新')).toBeInTheDocument()
    expect(screen.getByLabelText('标题')).toHaveValue('我的未保存标题')
    await user.click(screen.getByRole('button', { name: '保留当前内容' }))
    expect(screen.getByText(/当前页面内容已保留/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存草稿' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: '重新加载最新版本' }))
    expect(screen.getByText(/重新加载会丢弃当前页面/)).toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: '放弃当前内容并重新加载' }),
    )

    await waitFor(() =>
      expect(screen.getByLabelText('标题')).toHaveValue('服务端最新标题'),
    )
    expect(screen.getByText(/当前版本 4/)).toBeInTheDocument()
    expect(screen.queryByText('草稿已被其他操作更新')).not.toBeInTheDocument()
  })

  it('keeps local content visible when reloading the latest version fails', async () => {
    const user = userEvent.setup()
    let detailRequestCount = 0
    server.use(
      http.get(detailUrl, () => {
        detailRequestCount += 1
        return detailRequestCount === 1
          ? HttpResponse.json(detail())
          : HttpResponse.error()
      }),
      http.put(detailUrl, () =>
        HttpResponse.json(
          backendError('CONCURRENT_MODIFICATION', 'Version is stale'),
          { status: 409 },
        ),
      ),
    )

    await renderEditor()
    await user.clear(screen.getByLabelText('标题'))
    await user.type(screen.getByLabelText('标题'), '必须保留的本地标题')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))
    await user.click(
      await screen.findByRole('button', { name: '重新加载最新版本' }),
    )
    await user.click(
      screen.getByRole('button', { name: '放弃当前内容并重新加载' }),
    )

    expect(
      await screen.findByText('无法连接服务，请检查网络和后端服务状态。'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('标题')).toHaveValue('必须保留的本地标题')
    expect(screen.getByText(/重新加载会丢弃当前页面/)).toBeInTheDocument()
  })

  it('refreshes server state and stops editing after a business conflict', async () => {
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
      http.put(detailUrl, () =>
        HttpResponse.json(
          backendError('BUSINESS_CONFLICT', 'Request is no longer editable'),
          { status: 409 },
        ),
      ),
    )

    await renderEditor()
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(await screen.findByText('当前申请不可编辑')).toBeInTheDocument()
    expect(screen.getByText('待审批（SUBMITTED）')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '保存草稿' })).not.toBeInTheDocument()
    expect(detailRequestCount).toBe(2)
  })

  it('keeps the local form after a network failure', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(detailUrl, () => HttpResponse.json(detail())),
      http.put(detailUrl, () => HttpResponse.error()),
    )

    await renderEditor()
    await user.clear(screen.getByLabelText('标题'))
    await user.type(screen.getByLabelText('标题'), '网络失败时保留')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(
      await screen.findByText('无法连接服务，请检查网络和后端服务状态。'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('标题')).toHaveValue('网络失败时保留')
  })

  it('asks before leaving with unsaved changes', async () => {
    const user = userEvent.setup()
    server.use(http.get(detailUrl, () => HttpResponse.json(detail())))

    const { router } = await renderEditor()
    fireEvent.change(screen.getByLabelText('标题'), {
      target: { value: '未保存标题' },
    })
    await user.click(screen.getByRole('link', { name: '返回申请详情' }))

    expect(screen.getByText('有未保存的修改')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(
      `/requester/requests/${requestId}/edit`,
    )
    await user.click(screen.getByRole('button', { name: '继续编辑' }))
    expect(router.state.location.pathname).toBe(
      `/requester/requests/${requestId}/edit`,
    )

    await user.click(screen.getByRole('link', { name: '返回申请详情' }))
    await user.click(screen.getByRole('button', { name: '放弃修改并离开' }))
    await waitFor(() =>
      expect(router.state.location.pathname).toBe(
        `/requester/requests/${requestId}`,
      ),
    )
  })
})
