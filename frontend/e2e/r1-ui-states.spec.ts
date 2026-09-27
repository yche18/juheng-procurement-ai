import { expect, test } from '@playwright/test'

import { login, navigateWithinSession } from './support/ui'

const emptyPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
}

test.describe('R1 页面状态', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('展示真实查询等待状态', async ({ page }) => {
    await page.route('**/api/procurement-requests/*', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1_000))
      await route.continue()
    })
    await navigateWithinSession(
      page,
      '/requester/requests/00000000-0000-0000-0000-000000000034',
    )
    await expect(page.locator('.ant-skeleton')).toBeVisible()
    await expect(
      page.getByText('申请不存在或不可访问', { exact: true }),
    ).toBeVisible()
  })

  test('展示空列表而不误报错误', async ({ page }) => {
    await page.route('**/api/procurement-requests?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(emptyPage),
      })
    })
    await navigateWithinSession(page, '/requester/requests')
    await expect(page.getByText('你还没有创建采购申请。')).toBeVisible()
  })

  test('区分网络失败和服务端通用错误', async ({ page }) => {
    await page.route('**/api/procurement-requests?*', async (route) => {
      await route.abort('failed')
    })
    await navigateWithinSession(page, '/requester/requests')
    await expect(page.getByText('申请列表加载失败')).toBeVisible()
    await expect(page.getByText(/无法连接服务/)).toBeVisible()

    await page.unrouteAll({ behavior: 'wait' })
    await page.route('**/api/procurement-requests?*', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'malformed error contract' }),
      })
    })
    await page.getByRole('button', { name: '重 试' }).click()
    await expect(page.getByText('申请列表加载失败')).toBeVisible()
    await expect(page.getByText(/服务返回了无法识别的错误响应/)).toBeVisible()
  })
})
