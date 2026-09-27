import { Alert, Button, Card, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { Link, useSearchParams } from 'react-router-dom'

import { getApiErrorMessage } from '../../../shared/api/apiError'
import {
  formatDateTime,
  formatMoney,
} from '../../../shared/model/displayFormatters'
import { parsePositiveInteger } from '../../../shared/model/urlSearchParams'
import { useGetApprovalTasksQuery } from '../api/approvalApi'
import { ApprovalTaskStatusTag } from '../components/ApprovalTaskStatusTag'
import { isApprovalTaskPage } from '../model/approvalContract'
import {
  approvalTaskStatusLabels,
  isApprovalTaskStatus,
} from '../model/approvalPresentation'
import {
  approvalTaskStatuses,
  type ApprovalTaskStatus,
  type ApprovalTaskSummaryResponse,
} from '../types/approval'

const DEFAULT_PAGE = 1
const DEFAULT_SIZE = 20

const columns: ColumnsType<ApprovalTaskSummaryResponse> = [
  {
    title: '申请业务编号',
    dataIndex: ['procurementRequest', 'businessNumber'],
    render: (value: string, task) => (
      <Link to={`/approver/tasks/${task.id}`}>{value}</Link>
    ),
  },
  {
    title: '标题',
    dataIndex: ['procurementRequest', 'title'],
  },
  {
    title: '申请人',
    dataIndex: ['procurementRequest', 'creatorId'],
  },
  {
    title: '申请部门',
    dataIndex: ['procurementRequest', 'department'],
  },
  {
    title: '期望交付日期',
    dataIndex: ['procurementRequest', 'expectedDeliveryDate'],
  },
  {
    title: '预计总额',
    dataIndex: ['procurementRequest', 'estimatedTotal'],
    align: 'right',
    render: (value: number, task) =>
      formatMoney(value, task.procurementRequest.currency),
  },
  {
    title: '任务状态',
    dataIndex: 'status',
    render: (value: string) => <ApprovalTaskStatusTag status={value} />,
  },
  {
    title: '任务版本',
    dataIndex: 'version',
  },
  {
    title: '任务更新时间',
    dataIndex: 'updatedAt',
    render: (value: string) => formatDateTime(value),
  },
]

export function ApprovalTaskListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePositiveInteger(searchParams.get('page'), DEFAULT_PAGE)
  const size = parsePositiveInteger(searchParams.get('size'), DEFAULT_SIZE, 100)
  const rawStatus = searchParams.get('status')
  const status =
    rawStatus && isApprovalTaskStatus(rawStatus) ? rawStatus : undefined
  const hasInvalidStatus = rawStatus !== null && status === undefined

  const { data, error, isLoading, isFetching, refetch } =
    useGetApprovalTasksQuery({
      page: page - 1,
      size,
      ...(status ? { status } : {}),
    })

  const hasContractMismatch = data !== undefined && !isApprovalTaskPage(data)
  const pageData = isApprovalTaskPage(data) ? data : undefined

  const updateStatus = (nextStatus: 'ALL' | ApprovalTaskStatus) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', '1')
    next.set('size', String(size))
    if (nextStatus === 'ALL') {
      next.delete('status')
    } else {
      next.set('status', nextStatus)
    }
    setSearchParams(next)
  }

  const updatePagination = (pagination: TablePaginationConfig) => {
    const nextPage = pagination.current ?? DEFAULT_PAGE
    const nextSize = pagination.pageSize ?? DEFAULT_SIZE
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextSize === size ? nextPage : DEFAULT_PAGE))
    next.set('size', String(nextSize))
    if (status) {
      next.set('status', status)
    } else {
      next.delete('status')
    }
    setSearchParams(next)
  }

  const emptyDescription = status
    ? '当前状态筛选下没有审批任务。'
    : '当前没有分配给你的审批任务。'

  return (
    <Card>
      <div className={'page-heading-row'}>
        <div>
          <Typography.Title level={2}>审批任务</Typography.Title>
          <Typography.Paragraph type={'secondary'}>
            列表只展示服务端分配给当前审批人的任务。
          </Typography.Paragraph>
        </div>
      </div>

      <Space className={'list-toolbar'} wrap>
        <Typography.Text id={'approval-task-status-filter-label'}>
          任务状态
        </Typography.Text>
        <Select
          aria-labelledby={'approval-task-status-filter-label'}
          value={status ?? 'ALL'}
          className={'status-filter'}
          onChange={updateStatus}
          options={[
            { value: 'ALL', label: '全部状态' },
            ...approvalTaskStatuses.map((value) => ({
              value,
              label: `${approvalTaskStatusLabels[value]}（${value}）`,
            })),
          ]}
        />
        {isFetching && !isLoading ? (
          <Tag color={'processing'}>正在刷新</Tag>
        ) : null}
      </Space>

      {hasInvalidStatus ? (
        <Alert
          className={'page-alert'}
          type={'warning'}
          showIcon
          title={'URL 中包含未知任务状态筛选'}
          description={
            '未将“' + rawStatus + '”作为合法状态发送给服务端，请重新选择筛选条件。'
          }
        />
      ) : null}

      {error ? (
        <Alert
          className={'page-alert'}
          type={'error'}
          showIcon
          title={'审批任务列表加载失败'}
          description={getApiErrorMessage(error)}
          action={<Button onClick={() => void refetch()}>重试</Button>}
        />
      ) : null}

      {hasContractMismatch ? (
        <Alert
          className={'page-alert'}
          type={'error'}
          showIcon
          title={'服务响应与审批任务列表 Contract 不一致'}
          description={'响应字段缺失或类型不正确，页面未将其作为审批任务展示。'}
        />
      ) : null}

      <Table
        rowKey={'id'}
        columns={columns}
        dataSource={pageData?.content ?? []}
        loading={isLoading}
        scroll={{ x: 1350 }}
        locale={{
          emptyText:
            error || hasContractMismatch ? '暂无可展示数据' : emptyDescription,
        }}
        pagination={{
          current: page,
          pageSize: size,
          total: pageData?.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={updatePagination}
      />
    </Card>
  )
}
