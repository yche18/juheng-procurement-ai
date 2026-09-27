import { Alert, Button, Card, Space, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { useAppSelector } from './hooks'

export function HomePage() {
  const roles = useAppSelector(
    (state) => state.session.currentUser?.roles ?? [],
  )
  const isAdminOnly = roles.length === 1 && roles.includes('ADMIN')
  const isRequester = roles.includes('REQUESTER')
  const isApprover = roles.includes('APPROVER')

  if (isAdminOnly) {
    return (
      <Card>
        <Typography.Title level={2}>当前版本无管理功能</Typography.Title>
        <Typography.Paragraph>
          ADMIN 角色不会自动获得采购申请或审批任务的数据访问范围。
        </Typography.Paragraph>
        <Alert
          type={'info'}
          showIcon
          title={'R1 不提供管理后台，后续能力必须由明确的 User Story 交付'}
        />
      </Card>
    )
  }

  return (
    <Card>
      <Typography.Title level={2}>R1 采购授权演示</Typography.Title>
      <Typography.Paragraph>
        申请人流程已支持创建、查看、编辑和提交；审批人可以查看分配给自己的任务与完整申请。批准、驳回和审计页面将按后续 GitHub Issue 逐项交付。
      </Typography.Paragraph>
      <Alert
        type={'info'}
        showIcon
        title={'后端仍是业务事实和权限判断的最终来源'}
      />
      <Space className={'home-actions'} wrap>
        {isRequester ? (
          <>
            <Button type={'primary'}>
              <Link to={'/requester/requests'}>查看我的申请</Link>
            </Button>
            <Button>
              <Link to={'/requester/requests/new'}>创建采购申请</Link>
            </Button>
          </>
        ) : null}
        {isApprover ? (
          <Button type={'primary'}>
            <Link to={'/approver/tasks'}>查看审批任务</Link>
          </Button>
        ) : null}
      </Space>
    </Card>
  )
}
