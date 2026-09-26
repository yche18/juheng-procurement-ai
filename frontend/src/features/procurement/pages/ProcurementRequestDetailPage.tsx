import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Result,
  Skeleton,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link, useParams } from 'react-router-dom'

import {
  getApiErrorMessage,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import {
  useGetFinalApprovalDecisionQuery,
  useGetProcurementRequestQuery,
} from '../api/procurementApi'
import { ProcurementStatusTag } from '../components/ProcurementStatusTag'
import {
  isFinalApprovalDecision,
  isProcurementRequestDetail,
} from '../model/procurementContract'
import {
  formatDateTime,
  formatMoney,
  getCategoryDisplay,
  getDecisionDisplay,
  isProcurementRequestStatus,
} from '../model/procurementPresentation'
import type { ProcurementItemResponse } from '../types/procurement'

function itemColumns(currency: string): ColumnsType<ProcurementItemResponse> {
  return [
    { title: '序号', dataIndex: 'lineNumber', width: 72 },
    { title: '名称', dataIndex: 'name' },
    {
      title: '品类',
      dataIndex: 'categoryCode',
      render: (value: string) => getCategoryDisplay(value),
    },
    { title: '规格说明', dataIndex: 'specification' },
    {
      title: '数量',
      dataIndex: 'quantity',
      render: (value: number, item) => `${String(value)} ${item.unit}`,
    },
    {
      title: '预计单价',
      dataIndex: 'estimatedUnitPrice',
      align: 'right',
      render: (value: number) => formatMoney(value, currency),
    },
    {
      title: '行金额',
      dataIndex: 'estimatedLineTotal',
      align: 'right',
      render: (value: number) => formatMoney(value, currency),
    },
  ]
}

export function ProcurementRequestDetailPage() {
  const { requestId = '' } = useParams()
  const requestQuery = useGetProcurementRequestQuery(requestId, {
    skip: requestId.length === 0,
  })
  const request = isProcurementRequestDetail(requestQuery.data)
    ? requestQuery.data
    : undefined
  const hasRequestContractMismatch =
    requestQuery.data !== undefined && request === undefined
  const shouldLoadDecision =
    request?.status === 'APPROVED' || request?.status === 'REJECTED'
  const decisionQuery = useGetFinalApprovalDecisionQuery(requestId, {
    skip: !shouldLoadDecision,
  })

  const renderFinalDecision = () => {
    if (!request) {
      return null
    }

    if (!isProcurementRequestStatus(request.status)) {
      return (
        <Alert
          type="warning"
          showIcon
          title="申请状态不是已知枚举"
          description="页面不会根据未知状态猜测是否存在最终审批结果。"
        />
      )
    }

    if (!shouldLoadDecision) {
      return <Empty description="当前状态尚无最终审批结果。" />
    }

    if (decisionQuery.isLoading) {
      return <Skeleton active paragraph={{ rows: 3 }} />
    }

    if (decisionQuery.error) {
      const description = isBackendErrorCode(
        decisionQuery.error,
        'RESOURCE_NOT_FOUND',
      )
        ? '最终审批结果尚不存在或不可访问。'
        : getApiErrorMessage(decisionQuery.error)
      return (
        <Alert
          type="error"
          showIcon
          title="最终审批结果加载失败"
          description={description}
          action={
            <Button onClick={() => void decisionQuery.refetch()}>重试</Button>
          }
        />
      )
    }

    if (!isFinalApprovalDecision(decisionQuery.data)) {
      return (
        <Alert
          type="error"
          showIcon
          title="服务响应与最终决定 Contract 不一致"
          description="响应字段缺失或类型不正确，页面未将其作为最终决定展示。"
        />
      )
    }

    const decision = decisionQuery.data
    if (decision.procurementRequestId !== request.id) {
      return (
        <Alert
          type="error"
          showIcon
          title="最终决定关联的申请不一致"
          description="为避免展示错误申请的审批事实，本次响应已被隐藏。"
        />
      )
    }

    const decisionDisplay = getDecisionDisplay(decision.decision)
    const decisionConflictsWithStatus =
      decisionDisplay.known && decision.decision !== request.status

    return (
      <>
        {decisionConflictsWithStatus ? (
          <Alert
            className="page-alert"
            type="warning"
            showIcon
            title="申请状态与最终决定不一致"
            description="页面分别展示服务端返回的两个事实，不会静默改写其中任何一个。"
          />
        ) : null}
        <Descriptions
          bordered
          column={{ xs: 1, sm: 2 }}
          items={[
            {
              key: 'decision',
              label: '最终决定',
              children: (
                <Tag color={decisionDisplay.color}>{decisionDisplay.label}</Tag>
              ),
            },
            { key: 'actorId', label: '操作人', children: decision.actorId },
            {
              key: 'decidedAt',
              label: '决定时间',
              children: formatDateTime(decision.decidedAt),
            },
            {
              key: 'comment',
              label: '审批意见',
              children: decision.comment || '无',
              span: 2,
            },
          ]}
        />
      </>
    )
  }

  if (requestQuery.isLoading) {
    return (
      <Card>
        <Skeleton active title paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (requestQuery.error) {
    const notFound = isBackendErrorCode(
      requestQuery.error,
      'RESOURCE_NOT_FOUND',
    )
    return (
      <Card>
        <Result
          status={notFound ? '404' : 'error'}
          title={notFound ? '申请不存在或不可访问' : '申请详情加载失败'}
          subTitle={
            notFound
              ? '服务端不会区分申请不存在与当前用户无权访问。'
              : getApiErrorMessage(requestQuery.error)
          }
          extra={
            notFound ? (
              <Button type="primary">
                <Link to="/requester/requests">返回我的申请</Link>
              </Button>
            ) : (
              <Button type="primary" onClick={() => void requestQuery.refetch()}>
                重试
              </Button>
            )
          }
        />
      </Card>
    )
  }

  if (hasRequestContractMismatch || !request) {
    return (
      <Card>
        <Result
          status="error"
          title="服务响应与申请详情 Contract 不一致"
          subTitle="响应字段缺失或类型不正确，页面未将其作为采购申请展示。"
          extra={
            <Button type="primary" onClick={() => void requestQuery.refetch()}>
              重试
            </Button>
          }
        />
      </Card>
    )
  }

  return (
    <div className="request-detail-stack">
      <Card>
        <div className="page-heading-row">
          <div>
            <Typography.Title level={2}>{request.title}</Typography.Title>
            <Typography.Text type="secondary">
              {request.businessNumber}
            </Typography.Text>
          </div>
          <Button>
            <Link to="/requester/requests">返回我的申请</Link>
          </Button>
        </div>

        {!isProcurementRequestStatus(request.status) ? (
          <Alert
            className="page-alert"
            type="warning"
            showIcon
            title="服务端返回了未知申请状态"
            description="页面保留原始代码，不会把它映射为任何已知业务状态。"
          />
        ) : null}

        <Descriptions
          bordered
          column={{ xs: 1, sm: 2 }}
          items={[
            {
              key: 'status',
              label: '状态',
              children: <ProcurementStatusTag status={request.status} />,
            },
            { key: 'department', label: '申请部门', children: request.department },
            {
              key: 'expectedDeliveryDate',
              label: '期望交付日期',
              children: request.expectedDeliveryDate,
            },
            { key: 'currency', label: '币种', children: request.currency },
            {
              key: 'estimatedTotal',
              label: '服务端预计总额',
              children: formatMoney(request.estimatedTotal, request.currency),
            },
            { key: 'version', label: '版本', children: request.version },
            {
              key: 'createdAt',
              label: '创建时间',
              children: formatDateTime(request.createdAt),
            },
            {
              key: 'updatedAt',
              label: '更新时间',
              children: formatDateTime(request.updatedAt),
            },
            {
              key: 'purpose',
              label: '采购目的',
              children: request.purpose,
              span: 2,
            },
          ]}
        />

        <Typography.Title level={3} className="detail-section-title">
          采购项
        </Typography.Title>
        <Table
          rowKey="id"
          columns={itemColumns(request.currency)}
          dataSource={request.items}
          pagination={false}
          scroll={{ x: 980 }}
        />
      </Card>
      <Card title="最终审批结果">{renderFinalDecision()}</Card>
    </div>
  )
}
