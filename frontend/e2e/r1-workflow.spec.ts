import { expect, test } from '@playwright/test'

import {
  createApiContext,
  createDraft,
  demoUsers,
  submitDraft,
  uniqueTestTitle,
} from './support/api'
import {
  createDraftThroughUi,
  login,
  logout,
  navigateWithinSession,
  submitDraftThroughUi,
} from './support/ui'

test.describe('R1 主业务闭环', () => {
  test('登录保留完整角色导航，刷新后返回登录页', async ({ page }) => {
    await login(page, demoUsers.multiRole)
    await expect(
      page.getByRole('link', { name: '我的申请', exact: true }),
    ).toBeVisible()
    await expect(page.getByRole('link', { name: '创建申请' })).toBeVisible()
    await expect(
      page.getByRole('link', { name: '审批任务', exact: true }),
    ).toBeVisible()
    await expect(page.getByText('REQUESTER', { exact: true })).toBeVisible()
    await expect(page.getByText('APPROVER', { exact: true })).toBeVisible()

    await page.reload()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('button', { name: '登 录' })).toBeVisible()
  })

  test('申请人创建、编辑和提交，审批人批准，重新登录后读取最终事实', async ({
    page,
  }) => {
    const title = uniqueTestTitle('approve')
    const editedTitle = `${title}-edited`

    await login(page)
    const draft = await createDraftThroughUi(page, title)
    await page.getByRole('link', { name: '查看申请详情' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()

    await page.getByRole('link', { name: '编辑草稿' }).click()
    await page.getByLabel('标题').fill(editedTitle)
    await page.getByRole('button', { name: '保存草稿' }).click()
    await expect(
      page.getByText('采购申请草稿已保存', { exact: true }),
    ).toBeVisible()
    await page.getByRole('link', { name: '查看申请详情' }).click()

    const submission = await submitDraftThroughUi(page)
    await logout(page)

    await login(page, demoUsers.approver)
    await navigateWithinSession(page, '/approver/tasks')
    const taskRow = page.getByRole('row').filter({ hasText: editedTitle })
    await expect(taskRow).toBeVisible()
    await taskRow.getByRole('link').click()
    await expect(page).toHaveURL(
      new RegExp(`/approver/tasks/${submission.approvalTaskId}$`),
    )
    await page.getByRole('button', { name: '批准申请' }).click()
    await page.getByLabel('批准意见（可选）').fill('FE-017 批准验收通过')
    await page.getByRole('button', { name: '确认批准' }).click()
    await expect(page.getByText('申请已批准', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '关 闭' }).click()
    await expect(page.getByText('批准采购申请（APPROVAL_TASK_APPROVED）')).toBeVisible()

    await logout(page)
    await login(page, demoUsers.approver)
    await navigateWithinSession(
      page,
      `/approver/tasks/${submission.approvalTaskId}`,
    )
    await expect(
      page.getByText('已批准（APPROVED）', { exact: true }).first(),
    ).toBeVisible()
    await expect(page.getByText('FE-017 批准验收通过')).toBeVisible()

    await logout(page)
    await login(page)
    await navigateWithinSession(page, `/requester/requests/${draft.id}`)
    await expect(
      page.getByText('已批准（APPROVED）', { exact: true }).first(),
    ).toBeVisible()
    await expect(page.getByText('FE-017 批准验收通过')).toBeVisible()
    await expect(page.getByText('修改采购申请（PROCUREMENT_REQUEST_UPDATED）')).toBeVisible()
    await expect(page.getByText('批准采购申请（APPROVAL_TASK_APPROVED）')).toBeVisible()
  })

  test('审批人必须填写驳回原因，申请人重新登录后能读取该原因', async ({
    page,
    baseURL,
  }) => {
    if (!baseURL) {
      throw new Error('Playwright baseURL 未配置')
    }
    const title = uniqueTestTitle('reject')
    const requesterApi = await createApiContext(baseURL, demoUsers.requester)
    const draft = await createDraft(requesterApi, title)
    const submission = await submitDraft(
      requesterApi,
      draft,
      `fe017-reject-submit-${draft.id}`,
    )
    await requesterApi.dispose()

    await login(page, demoUsers.approver)
    await navigateWithinSession(
      page,
      `/approver/tasks/${submission.approvalTaskId}`,
    )
    await page.getByRole('button', { name: '驳回申请' }).click()
    await page.getByRole('button', { name: '确认驳回' }).click()
    await expect(page.getByText('请输入驳回原因')).toBeVisible()
    await page.getByLabel('驳回原因').fill('预算依据不足，FE-017 驳回验收')
    await page.getByRole('button', { name: '确认驳回' }).click()
    await expect(page.getByText('申请已驳回', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '关 闭' }).click()

    await logout(page)
    await login(page)
    await navigateWithinSession(page, `/requester/requests/${draft.id}`)
    await expect(page.getByText('驳回（REJECTED）')).toBeVisible()
    await expect(
      page.getByText('预算依据不足，FE-017 驳回验收'),
    ).toBeVisible()
  })
})
