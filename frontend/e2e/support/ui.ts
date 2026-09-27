import { expect, type Page } from '@playwright/test'

import { demoPassword, demoUsers, type DraftResponse } from './api'

export async function login(
  page: Page,
  username: string = demoUsers.requester,
) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(demoPassword)
  await page.getByRole('button', { name: '登 录' }).click()
  await expect(page).not.toHaveURL(/\/login$/)
  await expect(page.getByText(username, { exact: true })).toBeVisible()
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: '退 出' }).click()
  await expect(page).toHaveURL(/\/login$/)
}

export async function navigateWithinSession(page: Page, path: string) {
  await page.evaluate((targetPath) => {
    window.history.pushState(null, '', targetPath)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
}

export async function createDraftThroughUi(
  page: Page,
  title: string,
): Promise<DraftResponse> {
  await navigateWithinSession(page, '/requester/requests/new')
  await page.getByLabel('标题').fill(title)
  await page.getByLabel('申请部门').fill('测试采购部')
  await page.getByLabel('采购目的').fill('FE-017 浏览器主流程验收')
  await page.getByLabel('期望交付日期').fill('2030-12-31')
  await page.getByLabel('名称').fill('浏览器验收笔记本')
  await page.getByLabel('品类').click()
  await page.getByRole('option', { name: /笔记本电脑/ }).click()
  await page.getByLabel('规格说明').fill('32GB 内存，浏览器验收专用')
  await page.getByLabel('数量').fill('2')
  await page.getByLabel('计量单位').fill('台')
  await page.getByLabel('预计单价（CNY）').fill('8000')

  const responsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return (
      response.request().method() === 'POST' &&
      url.pathname === '/api/procurement-requests'
    )
  })
  await page.getByRole('button', { name: '创建草稿' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(201)
  await expect(
    page.getByText('采购申请草稿已创建', { exact: true }),
  ).toBeVisible()
  return response.json() as Promise<DraftResponse>
}

export async function submitDraftThroughUi(page: Page) {
  await page.getByRole('button', { name: '提交审批' }).click()
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      /\/api\/procurement-requests\/[^/]+\/submit$/.test(
        new URL(response.url()).pathname,
      ),
  )
  await page.getByRole('button', { name: '确认提交' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  await expect(page.getByText('审批任务已创建', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '关 闭' }).click()
  return response.json() as Promise<{ approvalTaskId: string }>
}
