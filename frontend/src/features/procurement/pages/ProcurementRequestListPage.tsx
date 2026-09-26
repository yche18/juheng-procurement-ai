import { Alert, Button, Card, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { Link, useSearchParams } from 'react-router-dom'

import { getApiErrorMessage } from '../../../shared/api/apiError'
import { useGetProcurementRequestsQuery } from '../api/procurementApi'
import { ProcurementStatusTag } from '../components/ProcurementStatusTag'
import { isProcurementRequestPage } from '../model/procurementContract'
import {
  formatDateTime,
  formatMoney,
  isProcurementRequestStatus,
  procurementStatusLabels,
} from '../model/procurementPresentation'
import {
  procurementRequestStatuses,
  type ProcurementRequestSummaryResponse,
  type ProcurementRequestStatus,
} from '../types/procurement'

const DEFAULT_PAGE = 1
const DEFAULT_SIZE = 20

function parsePositiveInteger(
  value: string | null,
  fallback: number,
  maximum?: number,
): number {
  if (!value || !/^\d+$/.test(value)) {
    return fallback
  }

  const parsed = Number(value)
  if (parsed < 1 || (maximum !== undefined && parsed > maximum)) {
    return fallback
  }

  return parsed
}

const columns: ColumnsType<ProcurementRequestSummaryResponse> = [
  {
    title: '业务编号',
    dataIndex: 'businessNumber',
    render: (value: string, request) => (
      <Link to={`/requester/requests/${request.id}`}>{value}</Link>
    ),
  },
  { title: '标题', dataIndex: 'title' },
  { title: '申请部门', dataIndex: 'department' },
  { title: '期望交付日期', dataIndex: 'expectedDeliveryDate' },
  {
    title: '预计总额',
    dataIndex: 'estimatedTotal',
    align: 'right',
    render: (value: number, request) => formatMoney(value, request.currency),
  },
  {
    title: '状态',
    dataIndex: 'status',
    render: (value: string) => <ProcurementStatusTag status={value} />,
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    render: (value: string) => formatDateTime(value),
  },
]

export function ProcurementRequestListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePositiveInteger(searchParams.get('page'), DEFAULT_PAGE)
  const size = parsePositiveInteger(searchParams.get('size'), DEFAULT_SIZE, 100)
  const rawStatus = searchParams.get('status')
  const status =
    rawStatus && isProcurementRequestStatus(rawStatus) ? rawStatus : undefined
  const hasInvalidStatus = rawStatus !== null && status === undefined

  const { data, error, isLoading, isFetching, refetch } =
    useGetProcurementRequestsQuery({
      page: page - 1,
      size,
      ...(status ? { status } : {}),
    })

  const hasContractMismatch = data !== undefined && !isProcurementRequestPage(data)
  const pageData = isProcurementRequestPage(data) ? data : undefined

  const updateStatus = (nextStatus: 'ALL' | ProcurementRequestStatus) => {
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
    ? '当前状态筛选下没有申请。'
    : '你还没有创建采购申请。'

  return (
    <Card>
      <div className="page-heading-row">
        <div>
          <Typography.Title level={2}>我的申请</Typography.Title>
          <Typography.Paragraph type="secondary">
            列表只展示服务端授权给当前申请人的采购申请。
          </Typography.Paragraph>
        </div>
        <Button type="primary">
          <Link to="/requester/requests/new">创建采购申请</Link>
        </Button>
      </div>

      <Space className="request-list-toolbar" wrap>
        <Typography.Text id="request-status-filter-label">
          申请状态
        </Typography.Text>
        <Select
          aria-labelledby="request-status-filter-label"
          value={status ?? 'ALL'}
          className="status-filter"
          onChange={updateStatus}
          options={[
            { value: 'ALL', label: '全部状态' },
            ...procurementRequestStatuses.map((value) => ({
              value,
              label: `${procurementStatusLabels[value]}（${value}）`,
            })),
          ]}
        />
        {isFetching && !isLoading ? (
          <Tag color="processing">正在刷新</Tag>
        ) : null}
      </Space>

      {hasInvalidStatus ? (
        <Alert
          className="page-alert"
          type="warning"
          showIcon
          title="URL 中包含未知状态筛选"
          description={'未将“' + rawStatus + '”作为合法状态发送给服务端，请重新选择筛选条件。'}
        />
      ) : null}

      {error ? (
        <Alert
          className="page-alert"
          type="error"
          showIcon
          title="申请列表加载失败"
          description={getApiErrorMessage(error)}
          action={<Button onClick={() => void refetch()}>重试</Button>}
        />
      ) : null}

      {hasContractMismatch ? (
        <Alert
          className="page-alert"
          type="error"
          showIcon
          title="服务响应与列表 Contract 不一致"
          description="响应字段缺失或类型不正确，页面未将其作为采购申请展示。"
        />
      ) : null}

      <Table
        rowKey="id"
        columns={columns}
        dataSource={pageData?.content ?? []}
        loading={isLoading}
        scroll={{ x: 1000 }}
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
