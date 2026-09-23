import { Alert, Card, Typography } from 'antd'

import { useAppSelector } from './hooks'

export function HomePage() {
  const roles = useAppSelector(
    (state) => state.session.currentUser?.roles ?? [],
  )
  const isAdminOnly = roles.length === 1 && roles.includes('ADMIN')

  if (isAdminOnly) {
    return (
      <Card>
        <Typography.Title level={2}>当前版本无管理功能</Typography.Title>
        <Typography.Paragraph>
          ADMIN 角色不会自动获得采购申请或审批任务的数据访问范围。
        </Typography.Paragraph>
        <Alert
          type="info"
          showIcon
          title="R1 不提供管理后台，后续能力必须由明确的 User Story 交付"
        />
      </Card>
    )
  }

  return (
    <Card>
      <Typography.Title level={2}>R1 前端基础已就绪</Typography.Title>
      <Typography.Paragraph>
        当前版本只建立认证、路由、状态和 API 基础设施。采购与审批页面将按
        GitHub Issue 逐项交付。
      </Typography.Paragraph>
      <Alert
        type="info"
        showIcon
        title="后端仍是业务事实和权限判断的最终来源"
      />
    </Card>
  )
}
