import {
  Alert,
  Button,
  Card,
  Descriptions,
  Result,
  Skeleton,
  Typography,
} from 'antd'
import { Link, useParams } from 'react-router-dom'

import {
  ProcurementRequestReadOnlyDetails,
} from '../../procurement/public'
import {
  getApiErrorMessage,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import { formatDateTime } from '../../../shared/model/displayFormatters'
import { useGetApprovalTaskQuery } from '../api/approvalApi'
import { ApprovalDecisionPanel } from '../components/ApprovalDecisionPanel'
import { ApprovalTaskStatusTag } from '../components/ApprovalTaskStatusTag'
import { isApprovalTaskDetail } from '../model/approvalContract'
import { isApprovalTaskStatus } from '../model/approvalPresentation'

export function ApprovalTaskDetailPage() {
  const { taskId = '' } = useParams()
  const taskQuery = useGetApprovalTaskQuery(taskId, {
    skip: taskId.length === 0,
  })
  const task = isApprovalTaskDetail(taskQuery.data)
    ? taskQuery.data
    : undefined
  const hasContractMismatch =
    taskQuery.data !== undefined && task === undefined

  if (taskQuery.isLoading) {
    return (
      <Card>
        <Skeleton active title paragraph={{ rows: 10 }} />
      </Card>
    )
  }

  if (taskQuery.error && !task) {
    const notFound = isBackendErrorCode(
      taskQuery.error,
      'RESOURCE_NOT_FOUND',
    )
    return (
      <Card>
        <Result
          status={notFound ? '404' : 'error'}
          title={
            notFound
              ? '审批任务不存在或不可访问'
              : '审批任务详情加载失败'
          }
          subTitle={
            notFound
              ? '服务端不会区分任务不存在与当前用户不是任务受理人。'
              : getApiErrorMessage(taskQuery.error)
          }
          extra={
            notFound ? (
              <Button type={'primary'}>
                <Link to={'/approver/tasks'}>返回审批任务</Link>
              </Button>
            ) : (
              <Button type={'primary'} onClick={() => void taskQuery.refetch()}>
                重试
              </Button>
            )
          }
        />
      </Card>
    )
  }

  if (hasContractMismatch || !task) {
    return (
      <Card>
        <Result
          status={'error'}
          title={'服务响应与审批任务详情 Contract 不一致'}
          subTitle={'响应字段缺失或类型不正确，页面未将其作为审批任务展示。'}
          extra={
            <Button type={'primary'} onClick={() => void taskQuery.refetch()}>
              重试
            </Button>
          }
        />
      </Card>
    )
  }

  const request = task.procurementRequest

  return (
    <div className={'request-detail-stack'}>
      <Card>
        <div className={'page-heading-row'}>
          <div>
            <Typography.Title level={2}>审批任务详情</Typography.Title>
            <Typography.Text type={'secondary'}>{task.id}</Typography.Text>
          </div>
          <Button>
            <Link to={'/approver/tasks'}>返回审批任务</Link>
          </Button>
        </div>

        {!isApprovalTaskStatus(task.status) ? (
          <Alert
            className={'page-alert'}
            type={'warning'}
            showIcon
            title={'服务端返回了未知审批任务状态'}
            description={'页面保留原始代码，不会把它映射为任何已知任务状态。'}
          />
        ) : null}

        <Descriptions
          bordered
          column={{ xs: 1, sm: 2 }}
          items={[
            {
              key: 'status',
              label: '任务状态',
              children: <ApprovalTaskStatusTag status={task.status} />,
            },
            {
              key: 'assigneeId',
              label: '受理审批人',
              children: task.assigneeId,
            },
            { key: 'version', label: '任务版本', children: task.version },
            {
              key: 'createdAt',
              label: '任务创建时间',
              children: formatDateTime(task.createdAt),
            },
            {
              key: 'updatedAt',
              label: '任务更新时间',
              children: formatDateTime(task.updatedAt),
            },
          ]}
        />
      </Card>

      <Card title={'人工审批决定'}>
        <ApprovalDecisionPanel
          task={task}
          refreshTask={() => taskQuery.refetch().unwrap()}
        />
      </Card>

      <Card title={'采购申请（只读）'}>
        <div className={'page-heading-row'}>
          <div>
            <Typography.Title level={3}>{request.title}</Typography.Title>
            <Typography.Text type={'secondary'}>
              {request.businessNumber}
            </Typography.Text>
          </div>
        </div>
        <Alert
          className={'page-alert'}
          type={'info'}
          showIcon
          title={'审批任务详情只展示服务端申请事实'}
          description={'采购申请正文不可在审批页面修改；批准或驳回只能通过上方两个明确的人工命令执行。'}
        />
        <ProcurementRequestReadOnlyDetails request={request} />
      </Card>
    </div>
  )
}
