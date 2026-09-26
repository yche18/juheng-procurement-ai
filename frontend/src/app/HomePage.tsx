import { Alert, Button, Card, Space, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { useAppSelector } from './hooks'

export function HomePage() {
  const roles = useAppSelector(
    (state) => state.session.currentUser?.roles ?? [],
  )
  const isAdminOnly = roles.length === 1 && roles.includes('ADMIN')
  const isRequester = roles.includes('REQUESTER')

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
      <Typography.Title level={2}>R1 采购授权演示</Typography.Title>
      <Typography.Paragraph>
        采购申请创建页面已经开放；列表、编辑、提交和审批页面将按后续
        GitHub Issue 逐项交付。
      </Typography.Paragraph>
      <Alert
        type="info"
        showIcon
        title="后端仍是业务事实和权限判断的最终来源"
      />
      {isRequester ? (
        <Space className="home-actions">
          <Button type="primary">
            <Link to="/requester/requests/new">创建采购申请</Link>
          </Button>
        </Space>
      ) : null}
    </Card>
  )
}
