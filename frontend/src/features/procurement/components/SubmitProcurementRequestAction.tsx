import {
  Alert,
  Button,
  Descriptions,
  Modal,
  Result,
  Typography,
} from 'antd'
import { useState } from 'react'

import {
  getApiErrorMessage,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import { formatMoney } from '../../../shared/model/displayFormatters'
import { useSubmitProcurementRequestMutation } from '../api/procurementApi'
import {
  isProcurementRequestDetail,
  isSubmitProcurementRequestResponse,
} from '../model/procurementContract'
import {
  clearSubmissionIntent,
  getOrCreateSubmissionIntent,
  type ProcurementSubmissionIntent,
} from '../model/procurementSubmission'
import type {
  ProcurementRequestDetailResponse,
  SubmitProcurementRequestResponse,
} from '../types/procurement'

type FeedbackKind =
  | 'routing'
  | 'in-progress'
  | 'idempotency-conflict'
  | 'business-conflict'
  | 'concurrent-modification'
  | 'uncertain'
  | 'contract'
  | 'failure'

interface SubmissionFeedback {
  kind: FeedbackKind
  title: string
  description: string
}

interface SubmitProcurementRequestActionProps {
  request: ProcurementRequestDetailResponse
  refreshRequest: () => Promise<unknown>
}

function isUncertainTransportError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'kind' in error &&
    (error.kind === 'network' || error.kind === 'timeout')
  )
}

function retryUsesOriginalKey(feedback: SubmissionFeedback | null): boolean {
  return (
    feedback === null ||
    feedback.kind === 'routing' ||
    feedback.kind === 'in-progress' ||
    feedback.kind === 'uncertain' ||
    feedback.kind === 'contract' ||
    feedback.kind === 'failure'
  )
}

export function SubmitProcurementRequestAction({
  request,
  refreshRequest,
}: SubmitProcurementRequestActionProps) {
  const [submitRequest, { isLoading: isSubmitting }] =
    useSubmitProcurementRequestMutation()
  const [isOpen, setIsOpen] = useState(false)
  const [intent, setIntent] =
    useState<ProcurementSubmissionIntent | null>(null)
  const [result, setResult] =
    useState<SubmitProcurementRequestResponse | null>(null)
  const [feedback, setFeedback] = useState<SubmissionFeedback | null>(null)
  const [isRefreshing, setIsRefreshing] = useState(false)

  const clearActiveIntent = (activeIntent: ProcurementSubmissionIntent) => {
    clearSubmissionIntent(activeIntent)
    setIntent(null)
  }

  const refreshAfterConflict = async (
    kind: 'business-conflict' | 'concurrent-modification',
    title: string,
    description: string,
  ) => {
    setIsRefreshing(true)
    setFeedback({ kind, title, description })

    try {
      const latest = await refreshRequest()
      if (!isProcurementRequestDetail(latest) || latest.id !== request.id) {
        setFeedback({
          kind,
          title,
          description: `${description} 最新详情响应不符合 Contract，页面没有据此推断业务状态。`,
        })
        return
      }

      setFeedback({
        kind,
        title,
        description: `${description} 当前服务端状态为 ${latest.status}，版本为 ${latest.version}。`,
      })
    } catch (error) {
      setFeedback({
        kind,
        title,
        description: `${description} 最新详情刷新失败：${getApiErrorMessage(error)}`,
      })
    } finally {
      setIsRefreshing(false)
    }
  }

  const handleSubmit = async () => {
    const activeIntent =
      intent ?? getOrCreateSubmissionIntent(request.id, request.version)
    setIntent(activeIntent)
    setFeedback(null)

    try {
      const response = await submitRequest({
        requestId: activeIntent.requestId,
        version: activeIntent.version,
        idempotencyKey: activeIntent.idempotencyKey,
      }).unwrap()

      if (
        !isSubmitProcurementRequestResponse(response) ||
        response.requestId !== request.id
      ) {
        setFeedback({
          kind: 'contract',
          title: '无法确认提交结果',
          description:
            '服务端成功响应与提交 Contract 不一致。页面保留原幂等键，不会把本次操作显示为成功。',
        })
        return
      }

      clearActiveIntent(activeIntent)
      setResult(response)
    } catch (error) {
      if (isBackendErrorCode(error, 'APPROVAL_ROUTING_FAILED')) {
        setFeedback({
          kind: 'routing',
          title: '审批路由失败',
          description:
            '系统无法确定唯一且非申请人本人的审批人。申请仍为草稿；路由配置修复后可使用原幂等键再次尝试。',
        })
        return
      }

      if (isBackendErrorCode(error, 'IDEMPOTENCY_IN_PROGRESS')) {
        setFeedback({
          kind: 'in-progress',
          title: '相同提交正在处理中',
          description:
            '服务端仍在处理相同业务意图。页面保留原幂等键，请稍后使用原键重试。',
        })
        return
      }

      if (isBackendErrorCode(error, 'IDEMPOTENCY_CONFLICT')) {
        setFeedback({
          kind: 'idempotency-conflict',
          title: '幂等键与既有请求冲突',
          description:
            '当前键已经绑定其他载荷。页面已停止提交，不会自动生成新键绕过冲突。',
        })
        return
      }

      if (isBackendErrorCode(error, 'BUSINESS_CONFLICT')) {
        clearActiveIntent(activeIntent)
        await refreshAfterConflict(
          'business-conflict',
          '申请状态已变化',
          '服务端拒绝了本次提交，页面已停止使用原提交意图并重新读取申请。',
        )
        return
      }

      if (isBackendErrorCode(error, 'CONCURRENT_MODIFICATION')) {
        clearActiveIntent(activeIntent)
        await refreshAfterConflict(
          'concurrent-modification',
          '申请版本已变化',
          '服务端拒绝了陈旧版本。页面已停止使用原提交意图，必须基于最新版本重新确认。',
        )
        return
      }

      if (isUncertainTransportError(error)) {
        setFeedback({
          kind: 'uncertain',
          title: '无法确认提交结果',
          description: `${getApiErrorMessage(error)} 页面已保留原版本和幂等键，重试时不会创建新的业务意图。`,
        })
        return
      }

      setFeedback({
        kind: 'failure',
        title: '提交失败',
        description: `${getApiErrorMessage(error)} 页面已保留原幂等键，不会自动重试。`,
      })
    }
  }

  const handleOpen = () => {
    setFeedback(null)
    setResult(null)
    setIntent(null)
    setIsOpen(true)
  }

  const handleClose = () => {
    if ((isSubmitting && !result && feedback === null) || isRefreshing) {
      return
    }

    if (
      intent &&
      (request.status !== 'DRAFT' ||
        feedback?.kind === 'idempotency-conflict' ||
        feedback?.kind === 'business-conflict' ||
        feedback?.kind === 'concurrent-modification')
    ) {
      clearActiveIntent(intent)
    }

    setIsOpen(false)
    setFeedback(null)
    setResult(null)
    setIntent(null)
  }

  const canSubmit =
    !result && request.status === 'DRAFT' && retryUsesOriginalKey(feedback)
  const retrying = feedback !== null && canSubmit
  const requiresExplicitExit =
    feedback?.kind === 'idempotency-conflict' ||
    feedback?.kind === 'concurrent-modification'
  const isBusy =
    (isSubmitting && !result && feedback === null) || isRefreshing
  const cancelText =
    feedback?.kind === 'idempotency-conflict'
      ? '放弃本次提交'
      : feedback?.kind === 'concurrent-modification'
        ? '关闭并基于最新版本重新确认'
        : '取消'

  return (
    <>
      {request.status === 'DRAFT' ? (
        <Button type="primary" onClick={handleOpen}>
          提交审批
        </Button>
      ) : null}

      <Modal
        title={result ? '采购申请已提交' : '确认提交采购申请'}
        open={isOpen}
        okText={result ? '关闭' : retrying ? '使用原幂等键重试' : '确认提交'}
        cancelText={cancelText}
        confirmLoading={isBusy}
        closable={!isBusy && !requiresExplicitExit}
        mask={{ closable: !isBusy && !requiresExplicitExit }}
        keyboard={!isBusy && !requiresExplicitExit}
        okButtonProps={{
          disabled: isBusy || (!result && !canSubmit),
        }}
        cancelButtonProps={{
          disabled: isBusy,
          style: result ? { display: 'none' } : undefined,
        }}
        onCancel={handleClose}
        onOk={result ? handleClose : () => void handleSubmit()}
      >
        {result ? (
          <Result
            status="success"
            title="审批任务已创建"
            subTitle="申请已进入人工审批流程，核心字段现在为只读。"
          >
            <Descriptions
              bordered
              column={{ xs: 1, sm: 1 }}
              items={[
                {
                  key: 'requestStatus',
                  label: '申请状态',
                  children: result.requestStatus,
                },
                {
                  key: 'requestVersion',
                  label: '申请新版本',
                  children: result.requestVersion,
                },
                {
                  key: 'approvalTaskId',
                  label: '审批任务 ID',
                  children: result.approvalTaskId,
                },
                {
                  key: 'approvalTaskStatus',
                  label: '审批任务状态',
                  children: result.approvalTaskStatus,
                },
              ]}
            />
          </Result>
        ) : (
          <>
            <Descriptions
              bordered
              column={{ xs: 1, sm: 1 }}
              items={[
                {
                  key: 'businessNumber',
                  label: '业务编号',
                  children: request.businessNumber,
                },
                { key: 'title', label: '标题', children: request.title },
                {
                  key: 'estimatedTotal',
                  label: '服务端预计总额',
                  children: formatMoney(
                    request.estimatedTotal,
                    request.currency,
                  ),
                },
                {
                  key: 'version',
                  label: '提交版本',
                  children: intent?.version ?? request.version,
                },
              ]}
            />
            <Alert
              className="page-alert"
              type="warning"
              showIcon
              title="提交后核心字段不可普通修改"
              description="确认后，服务端会重新校验所有权、草稿状态、当前版本和完整性，并创建人工审批任务。"
            />
            {feedback ? (
              <Alert
                className="page-alert"
                type={
                  feedback.kind === 'in-progress' ? 'info' : 'error'
                }
                showIcon
                title={feedback.title}
                description={feedback.description}
              />
            ) : null}
            {retrying ? (
              <Typography.Paragraph type="secondary">
                重试会复用本次业务意图的原版本和原幂等键，不会自动创建新键。
              </Typography.Paragraph>
            ) : null}
          </>
        )}
      </Modal>
    </>
  )
}
