import { expect, test } from '@playwright/test'

import {
  createApiContext,
  createDraft,
  createDraftPayload,
  demoUsers,
  uniqueTestTitle,
} from './support/api'
import { login, navigateWithinSession } from './support/ui'

test.describe('R1 权限、版本与幂等边界', () => {
  test('错误密码停留在应用登录页且响应不触发浏览器认证窗口', async ({
    page,
  }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill(demoUsers.requester)
    await page.getByLabel('密码').fill('incorrect-password')

    const authenticationFailure = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/current-user') && response.status() === 401,
    )
    await page.getByRole('button', { name: '登 录' }).click()

    const response = await authenticationFailure
    expect(response.headers()['www-authenticate']).toBeUndefined()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('登录失败', { exact: true })).toBeVisible()
    await expect(
      page.getByText('Authentication is required', { exact: true }),
    ).toBeVisible()
  })

  test('真实 401 与 403 保持不同语义，浏览器 401 清会话而 403 保留会话', async ({
    page,
    baseURL,
  }) => {
    if (!baseURL) {
      throw new Error('Playwright baseURL 未配置')
    }
    const anonymousApi = await createApiContext(baseURL)
    const unauthenticated = await anonymousApi.get('/api/current-user')
    expect(unauthenticated.status()).toBe(401)
    expect((await unauthenticated.json()) as { code: string }).toMatchObject({
      code: 'AUTHENTICATION_REQUIRED',
    })
    await anonymousApi.dispose()

    const requesterApi = await createApiContext(baseURL, demoUsers.requester)
    const forbidden = await requesterApi.get('/api/approval-tasks')
    expect(forbidden.status()).toBe(403)
    expect((await forbidden.json()) as { code: string }).toMatchObject({
      code: 'ACCESS_DENIED',
    })
    await requesterApi.dispose()

    await login(page)
    await navigateWithinSession(page, '/approver/tasks')
    await expect(page).toHaveURL(/\/403$/)
    await expect(page.getByText('无权访问', { exact: true })).toBeVisible()
    await expect(page.getByText(demoUsers.requester, { exact: true })).toBeVisible()

    await page.route('**/api/procurement-requests?*', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'AUTHENTICATION_REQUIRED',
          message: 'Authentication is required',
          path: '/api/procurement-requests',
          fieldErrors: [],
        }),
      })
    })
    await navigateWithinSession(page, '/requester/requests')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('他人资源保持不可枚举，前后端字段校验都生效', async ({
    page,
    baseURL,
  }) => {
    if (!baseURL) {
      throw new Error('Playwright baseURL 未配置')
    }
    const requesterApi = await createApiContext(baseURL, demoUsers.requester)
    const draft = await createDraft(requesterApi, uniqueTestTitle('hidden'))
    const invalid = await requesterApi.post('/api/procurement-requests', {
      data: createDraftPayload(''),
    })
    expect(invalid.status()).toBe(400)
    expect((await invalid.json()) as { code: string }).toMatchObject({
      code: 'VALIDATION_FAILED',
    })
    await requesterApi.dispose()

    await login(page, demoUsers.multiRole)
    await navigateWithinSession(page, `/requester/requests/${draft.id}`)
    await expect(
      page.getByText('申请不存在或不可访问', { exact: true }),
    ).toBeVisible()

    await navigateWithinSession(page, '/requester/requests/new')
    await page.getByRole('button', { name: '创建草稿' }).click()
    await expect(page.getByText('请输入标题')).toBeVisible()
    await expect(page.getByText('请输入申请部门')).toBeVisible()
    await expect(page.getByText('请输入采购目的')).toBeVisible()
  })

  test('陈旧版本、非法状态和相同幂等写保持服务端不变量', async ({
    baseURL,
  }) => {
    if (!baseURL) {
      throw new Error('Playwright baseURL 未配置')
    }
    const api = await createApiContext(baseURL, demoUsers.requester)
    const title = uniqueTestTitle('conflict')
    const draft = await createDraft(api, title)
    const payload = createDraftPayload(`${title}-updated`)

    const firstUpdate = await api.put(
      `/api/procurement-requests/${draft.id}`,
      { data: { ...payload, version: draft.version } },
    )
    await expect(firstUpdate).toBeOK()
    const updated = (await firstUpdate.json()) as { version: number }

    const staleUpdate = await api.put(
      `/api/procurement-requests/${draft.id}`,
      { data: { ...payload, title: `${title}-stale`, version: draft.version } },
    )
    expect(staleUpdate.status()).toBe(409)
    expect((await staleUpdate.json()) as { code: string }).toMatchObject({
      code: 'CONCURRENT_MODIFICATION',
    })

    const idempotencyKey = `fe017-duplicate-${draft.id}`
    const firstSubmit = await api.post(
      `/api/procurement-requests/${draft.id}/submit`,
      {
        headers: { 'Idempotency-Key': idempotencyKey },
        data: { version: updated.version },
      },
    )
    await expect(firstSubmit).toBeOK()
    const firstResult = (await firstSubmit.json()) as {
      requestVersion: number
      approvalTaskId: string
    }

    const replay = await api.post(
      `/api/procurement-requests/${draft.id}/submit`,
      {
        headers: { 'Idempotency-Key': idempotencyKey },
        data: { version: updated.version },
      },
    )
    await expect(replay).toBeOK()
    expect((await replay.json()) as object).toEqual(firstResult)

    const illegalUpdate = await api.put(
      `/api/procurement-requests/${draft.id}`,
      {
        data: {
          ...payload,
          title: `${title}-illegal`,
          version: firstResult.requestVersion,
        },
      },
    )
    expect(illegalUpdate.status()).toBe(409)
    expect((await illegalUpdate.json()) as { code: string }).toMatchObject({
      code: 'BUSINESS_CONFLICT',
    })
    await api.dispose()
  })
})
