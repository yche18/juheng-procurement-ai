import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../app/store'
import { sessionAuthenticated } from '../../identity/model/sessionSlice'
import { API_ORIGIN } from '../../../test/handlers'
import { renderApplication } from '../../../test/renderApplication'
import { server } from '../../../test/server'

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

function createdResponse() {
  return {
    id: '10000000-0000-0000-0000-000000000001',
    businessNumber: 'PR-20260926-1001',
    creatorId: 'demo-requester',
    title: '研发电脑采购',
    purpose: '补充开发设备',
    department: '研发部',
    expectedDeliveryDate: '2026-10-10',
    currency: 'CNY',
    estimatedTotal: 18000,
    status: 'DRAFT',
    version: 0,
    createdAt: '2026-09-26T01:00:00Z',
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
  }
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('标题'), '研发电脑采购')
  await user.type(screen.getByLabelText('申请部门'), '研发部')
  await user.type(screen.getByLabelText('采购目的'), '补充开发设备')
  fireEvent.change(screen.getByLabelText('期望交付日期'), {
    target: { value: '2026-10-10' },
  })
  await user.type(screen.getByLabelText('名称'), '开发笔记本')
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '品类' }))
  await user.click(await screen.findByText('笔记本电脑（LAPTOP）'))
  await user.type(screen.getByLabelText('规格说明'), '32GB 内存')
  await user.type(screen.getByLabelText('数量'), '2')
  await user.type(screen.getByLabelText('计量单位'), '台')
  await user.type(screen.getByLabelText('预计单价（CNY）'), '9000.00')
}

describe('CreateProcurementRequestPage', () => {
  it('allows REQUESTER access and rejects a user without the role', async () => {
    const requesterView = renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: requesterStore(),
    })

    expect(
      screen.getByRole('heading', { name: '创建采购申请' }),
    ).toBeInTheDocument()
    requesterView.unmount()

    const approverStore = createAppStore()
    approverStore.dispatch(
      sessionAuthenticated({
        userId: 'demo-approver',
        roles: ['APPROVER'],
      }),
    )
    renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: approverStore,
    })

    expect(await screen.findByText('无权访问')).toBeInTheDocument()
  })

  it('keeps one item minimum and supports adding and removing items', async () => {
    const user = userEvent.setup()
    renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: requesterStore(),
    })

    expect(screen.getByText('采购项 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /删\s*除/ })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: '添加采购项' }))
    expect(screen.getByText('采购项 2')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /删\s*除/ })[0]).toBeEnabled()

    await user.click(screen.getAllByRole('button', { name: /删\s*除/ })[1])
    expect(screen.queryByText('采购项 2')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /删\s*除/ })).toBeDisabled()
  })

  it('limits category choices and blocks values outside decimal constraints', async () => {
    const user = userEvent.setup()
    let requestCount = 0
    server.use(
      http.post(`${API_ORIGIN}/api/procurement-requests`, () => {
        requestCount += 1
        return HttpResponse.json(createdResponse(), { status: 201 })
      }),
    )

    renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: requesterStore(),
    })

    fireEvent.mouseDown(screen.getByRole('combobox', { name: '品类' }))
    const options = await screen.findAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual([
      '笔记本电脑（LAPTOP）',
      '显示器（MONITOR）',
      '办公椅（OFFICE_CHAIR）',
      '软件许可（SOFTWARE_LICENSE）',
    ])
    await user.keyboard('{Escape}')

    await fillValidForm(user)
    await user.clear(screen.getByLabelText('数量'))
    await user.type(screen.getByLabelText('数量'), '0')
    await user.clear(screen.getByLabelText('预计单价（CNY）'))
    await user.type(screen.getByLabelText('预计单价（CNY）'), '1.234')
    await user.click(screen.getByRole('button', { name: '创建草稿' }))

    expect(await screen.findByText('数量必须大于 0')).toBeInTheDocument()
    expect(
      await screen.findByText('预计单价最多 8 位整数和 2 位小数'),
    ).toBeInTheDocument()
    expect(requestCount).toBe(0)
  })

  it('posts only editable fields and shows the server result', async () => {
    const user = userEvent.setup()
    let requestBody: unknown
    server.use(
      http.post(`${API_ORIGIN}/api/procurement-requests`, async ({ request }) => {
        requestBody = await request.json()
        return HttpResponse.json(createdResponse(), { status: 201 })
      }),
    )

    renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: requesterStore(),
    })
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: '创建草稿' }))

    expect(await screen.findByText('采购申请草稿已创建')).toBeInTheDocument()
    expect(screen.getAllByText('PR-20260926-1001')).not.toHaveLength(0)
    expect(screen.getByText('DRAFT')).toBeInTheDocument()
    expect(screen.getByText('0')).toBeInTheDocument()
    expect(screen.getAllByText(/18,000\.00/)).not.toHaveLength(0)
    expect(
      screen.getByRole('link', { name: '查看申请详情' }),
    ).toHaveAttribute(
      'href',
      '/requester/requests/10000000-0000-0000-0000-000000000001',
    )
    expect(requestBody).toEqual({
      title: '研发电脑采购',
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
          estimatedUnitPrice: 9000,
        },
      ],
    })
  })

  it('maps backend item errors and keeps unknown errors visible', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${API_ORIGIN}/api/procurement-requests`, () =>
        HttpResponse.json(
          {
            code: 'VALIDATION_FAILED',
            message: 'Request validation failed',
            path: '/api/procurement-requests',
            fieldErrors: [
              {
                field: 'items[0].quantity',
                code: 'DecimalMin',
                message: '数量必须大于 0',
              },
              {
                field: 'serverOnlyField',
                code: 'Invalid',
                message: '服务端返回了未知字段错误',
              },
            ],
          },
          { status: 400 },
        ),
      ),
    )

    renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: requesterStore(),
    })
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: '创建草稿' }))

    expect(await screen.findByText('数量必须大于 0')).toBeInTheDocument()
    expect(
      screen.getByText(/服务端返回了未知字段错误/),
    ).toBeInTheDocument()
  })

  it('shows network failure and prevents a duplicate click while pending', async () => {
    const user = userEvent.setup()
    let requestCount = 0
    let releaseRequest: () => void = () => undefined
    const requestGate = new Promise<void>((resolve) => {
      releaseRequest = resolve
    })
    server.use(
      http.post(`${API_ORIGIN}/api/procurement-requests`, async () => {
        requestCount += 1
        await requestGate
        return HttpResponse.error()
      }),
    )

    renderApplication({
      initialEntries: ['/requester/requests/new'],
      store: requesterStore(),
    })
    await fillValidForm(user)
    const submitButton = screen.getByRole('button', { name: '创建草稿' })
    await user.click(submitButton)

    await waitFor(() => expect(submitButton).toBeDisabled())
    await user.click(submitButton)
    expect(requestCount).toBe(1)

    releaseRequest()
    expect(
      await screen.findByText('无法连接服务，请检查网络和后端服务状态。'),
    ).toBeInTheDocument()
  })
})
