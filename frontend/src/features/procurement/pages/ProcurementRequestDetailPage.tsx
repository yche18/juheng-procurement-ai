import {
  Alert,
  Button,
  Card,
  Empty,
  Result,
  Skeleton,
  Space,
  Typography,
} from 'antd'
import { Link, useParams } from 'react-router-dom'

import {
  getApiErrorMessage,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import {
  useGetFinalApprovalDecisionQuery,
  useGetProcurementRequestQuery,
} from '../api/procurementApi'
import { FinalApprovalDecisionDetails } from '../components/FinalApprovalDecisionDetails'
import { ProcurementRequestReadOnlyDetails } from '../components/ProcurementRequestReadOnlyDetails'
import { SubmitProcurementRequestAction } from '../components/SubmitProcurementRequestAction'
import {
  isFinalApprovalDecision,
  isProcurementRequestDetail,
} from '../model/procurementContract'
import { isProcurementRequestStatus } from '../model/procurementPresentation'

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
          type={'warning'}
          showIcon
          title={'申请状态不是已知枚举'}
          description={'页面不会根据未知状态猜测是否存在最终审批结果。'}
        />
      )
    }

    if (!shouldLoadDecision) {
      return <Empty description={'当前状态尚无最终审批结果。'} />
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
          type={'error'}
          showIcon
          title={'最终审批结果加载失败'}
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
          type={'error'}
          showIcon
          title={'服务响应与最终决定 Contract 不一致'}
          description={'响应字段缺失或类型不正确，页面未将其作为最终决定展示。'}
        />
      )
    }

    const decision = decisionQuery.data
    if (decision.procurementRequestId !== request.id) {
      return (
        <Alert
          type={'error'}
          showIcon
          title={'最终决定关联的申请不一致'}
          description={'为避免展示错误申请的审批事实，本次响应已被隐藏。'}
        />
      )
    }

    return (
      <FinalApprovalDecisionDetails
        decision={decision}
        expectedStatus={request.status}
        statusFactLabel={'申请状态'}
      />
    )
  }

  if (requestQuery.isLoading) {
    return (
      <Card>
        <Skeleton active title paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (requestQuery.error && !request) {
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
              <Button type={'primary'}>
                <Link to={'/requester/requests'}>返回我的申请</Link>
              </Button>
            ) : (
              <Button type={'primary'} onClick={() => void requestQuery.refetch()}>
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
          status={'error'}
          title={'服务响应与申请详情 Contract 不一致'}
          subTitle={'响应字段缺失或类型不正确，页面未将其作为采购申请展示。'}
          extra={
            <Button type={'primary'} onClick={() => void requestQuery.refetch()}>
              重试
            </Button>
          }
        />
      </Card>
    )
  }

  return (
    <div className={'request-detail-stack'}>
      <Card>
        <div className={'page-heading-row'}>
          <div>
            <Typography.Title level={2}>{request.title}</Typography.Title>
            <Typography.Text type={'secondary'}>
              {request.businessNumber}
            </Typography.Text>
          </div>
          <Space wrap>
            {request.status === 'DRAFT' ? (
              <Button>
                <Link to={`/requester/requests/${request.id}/edit`}>
                  编辑草稿
                </Link>
              </Button>
            ) : null}
            <SubmitProcurementRequestAction
              request={request}
              refreshRequest={() => requestQuery.refetch().unwrap()}
            />
            <Button>
              <Link to={'/requester/requests'}>返回我的申请</Link>
            </Button>
          </Space>
        </div>

        <ProcurementRequestReadOnlyDetails request={request} />
      </Card>
      <Card title={'最终审批结果'}>{renderFinalDecision()}</Card>
    </div>
  )
}
