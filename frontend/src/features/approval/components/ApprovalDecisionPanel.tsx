import {
  Alert,
  Button,
  Descriptions,
  Form,
  Input,
  Modal,
  Result,
  Skeleton,
  Space,
  Typography,
} from 'antd'
import { useState } from 'react'

import {
  FinalApprovalDecisionDetails,
  isFinalApprovalDecision,
  useGetFinalApprovalDecisionQuery,
} from '../../procurement/public'
import {
  getApiErrorMessage,
  isApiErrorResponse,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import { formatDateTime } from '../../../shared/model/displayFormatters'
import {
  useApproveApprovalTaskMutation,
  useRejectApprovalTaskMutation,
} from '../api/approvalApi'
import { isApprovalDecisionResponse, isApprovalTaskDetail } from '../model/approvalContract'
import {
  clearApprovalDecisionIntent,
  normalizeApprovalDecisionComment,
  resolveApprovalDecisionIntent,
  type ApprovalDecisionIntent,
} from '../model/approvalDecisionIntent'
import type {
  ApprovalDecision,
  ApprovalDecisionResponse,
  ApprovalTaskDetailResponse,
} from '../types/approval'

type FeedbackKind =
  | 'in-progress'
  | 'idempotency-conflict'
  | 'business-conflict'
  | 'concurrent-modification'
  | 'uncertain'
  | 'contract'
  | 'intent-conflict'
  | 'validation'
  | 'failure'

interface DecisionFeedback {
  kind: FeedbackKind
  title: string
  description: string
}

interface DecisionFormValues {
  comment?: string
}

interface ApprovalDecisionPanelProps {
  task: ApprovalTaskDetailResponse
  refreshTask: () => Promise<unknown>
}

function isUncertainTransportError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'kind' in error &&
    (error.kind === 'network' || error.kind === 'timeout')
  )
}

function getCommentFieldError(error: unknown): string | null {
  if (
    typeof error !== 'object' ||
    error === null ||
    !('kind' in error) ||
    error.kind !== 'backend' ||
    !('response' in error) ||
    !isApiErrorResponse(error.response) ||
    error.response.code !== 'VALIDATION_FAILED'
  ) {
    return null
  }

  return (
    error.response.fieldErrors.find((violation) => violation.field === 'comment')
      ?.message ?? null
  )
}

function retryUsesOriginalKey(feedback: DecisionFeedback | null): boolean {
  return (
    feedback === null ||
    feedback.kind === 'in-progress' ||
    feedback.kind === 'uncertain' ||
    feedback.kind === 'contract' ||
    feedback.kind === 'validation' ||
    feedback.kind === 'failure'
  )
}

export function ApprovalDecisionPanel({
  task,
  refreshTask,
}: ApprovalDecisionPanelProps) {
  const [form] = Form.useForm<DecisionFormValues>()
  const [approveTask, { isLoading: isApproving }] =
    useApproveApprovalTaskMutation()
  const [rejectTask, { isLoading: isRejecting }] =
    useRejectApprovalTaskMutation()
  const [mode, setMode] = useState<ApprovalDecision | null>(null)
  const [intent, setIntent] = useState<ApprovalDecisionIntent | null>(null)
  const [result, setResult] =
    useState<ApprovalDecisionResponse | null>(null)
  const [feedback, setFeedback] = useState<DecisionFeedback | null>(null)
  const [isRefreshing, setIsRefreshing] = useState(false)

  const request = task.procurementRequest
  const isTerminal = task.status === 'APPROVED' || task.status === 'REJECTED'
  const decisionQuery = useGetFinalApprovalDecisionQuery(request.id, {
    skip: !isTerminal,
  })
  const isSubmitting = isApproving || isRejecting
  const isBusy = isSubmitting || isRefreshing

  const clearActiveIntent = (activeIntent?: ApprovalDecisionIntent | null) => {
    clearApprovalDecisionIntent(task.id, activeIntent?.idempotencyKey)
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
      const latest = await refreshTask()
      if (!isApprovalTaskDetail(latest) || latest.id !== task.id) {
        setFeedback({
          kind,
          title,
          description: `${description} 最新任务响应不符合 Contract，页面没有据此推断业务状态。`,
        })
        return
      }

      setFeedback({
        kind,
        title,
        description: `${description} 当前服务端任务状态为 ${latest.status}，版本为 ${latest.version}。`,
      })
    } catch (error) {
      setFeedback({
        kind,
        title,
        description: `${description} 最新任务刷新失败：${getApiErrorMessage(error)}`,
      })
    } finally {
      setIsRefreshing(false)
    }
  }

  const handleOpen = (decision: ApprovalDecision) => {
    form.resetFields()
    setMode(decision)
    setIntent(null)
    setResult(null)
    setFeedback(null)
  }

  const handleSubmit = async () => {
    if (!mode) {
      return
    }

    let values: DecisionFormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    const comment = normalizeApprovalDecisionComment(values.comment)
    if (mode === 'REJECTED' && comment === null) {
      form.setFields([{ name: 'comment', errors: ['请输入驳回原因'] }])
      return
    }

    const resolution = await resolveApprovalDecisionIntent({
      taskId: task.id,
      approvalTaskVersion: task.version,
      decision: mode,
      comment,
    })
    if (resolution.kind === 'conflict') {
      setFeedback({
        kind: 'intent-conflict',
        title: '存在尚未明确结束的审批意图',
        description:
          `任务版本 ${resolution.existingVersion} 已保存一个${
            resolution.existingDecision === 'APPROVED' ? '批准' : '驳回'
          }意图。页面不会使用同一幂等键发送不同载荷；请明确放弃旧意图后再继续。`,
      })
      return
    }

    const activeIntent = resolution.intent
    setIntent(activeIntent)
    setFeedback(null)

    const mutationArgs = {
      taskId: activeIntent.taskId,
      requestId: request.id,
      approvalTaskVersion: activeIntent.approvalTaskVersion,
      comment: activeIntent.comment,
      idempotencyKey: activeIntent.idempotencyKey,
    }

    try {
      const response = await (activeIntent.decision === 'APPROVED'
        ? approveTask(mutationArgs)
        : rejectTask(mutationArgs)
      ).unwrap()

      if (
        !isApprovalDecisionResponse(response) ||
        response.approvalTaskId !== task.id ||
        response.decision !== activeIntent.decision
      ) {
        setFeedback({
          kind: 'contract',
          title: '无法确认审批结果',
          description:
            '服务端成功响应与审批决定 Contract 不一致。页面保留原任务版本、决定载荷和幂等键，不会把本次操作显示为成功。',
        })
        return
      }

      clearActiveIntent(activeIntent)
      setResult(response)
    } catch (error) {
      const commentError = getCommentFieldError(error)
      if (commentError) {
        clearActiveIntent(activeIntent)
        form.setFields([{ name: 'comment', errors: [commentError] }])
        setFeedback({
          kind: 'validation',
          title: '审批意见未通过服务端校验',
          description: '请修正意见后重新确认；修改载荷时将使用新的幂等键。',
        })
        return
      }

      if (isBackendErrorCode(error, 'IDEMPOTENCY_IN_PROGRESS')) {
        setFeedback({
          kind: 'in-progress',
          title: '相同审批决定正在处理中',
          description:
            '服务端仍在处理相同业务意图。页面保留原任务版本、载荷和幂等键，请稍后使用原键重试。',
        })
        return
      }

      if (isBackendErrorCode(error, 'IDEMPOTENCY_CONFLICT')) {
        setFeedback({
          kind: 'idempotency-conflict',
          title: '幂等键与既有审批请求冲突',
          description:
            '当前键已绑定其他载荷。页面已停止决定操作，不会自动生成新键绕过冲突。',
        })
        return
      }

      if (isBackendErrorCode(error, 'BUSINESS_CONFLICT')) {
        await refreshAfterConflict(
          'business-conflict',
          '审批任务状态已变化',
          '服务端拒绝了本次决定；页面保留原决定内容用于说明，但不会继续使用陈旧业务状态。',
        )
        return
      }

      if (isBackendErrorCode(error, 'CONCURRENT_MODIFICATION')) {
        await refreshAfterConflict(
          'concurrent-modification',
          '审批任务版本已变化',
          '服务端拒绝了陈旧任务版本；页面保留原决定内容用于说明，必须基于最新任务重新确认。',
        )
        return
      }

      if (isUncertainTransportError(error)) {
        setFeedback({
          kind: 'uncertain',
          title: '无法确认审批结果',
          description: `${getApiErrorMessage(error)} 页面已保留原任务版本、决定载荷和幂等键，重试不会创建新的业务意图。`,
        })
        return
      }

      setFeedback({
        kind: 'failure',
        title: '审批决定提交失败',
        description: `${getApiErrorMessage(error)} 页面保留原幂等键且不会自动重试。`,
      })
    }
  }

  const requiresExplicitAbandon =
    feedback?.kind === 'idempotency-conflict' ||
    feedback?.kind === 'business-conflict' ||
    feedback?.kind === 'concurrent-modification' ||
    feedback?.kind === 'intent-conflict'
  const canRetry =
    task.status === 'PENDING' &&
    result === null &&
    retryUsesOriginalKey(feedback)
  const retrying = intent !== null && feedback !== null && canRetry

  const closeModal = () => {
    setMode(null)
    setIntent(null)
    setResult(null)
    setFeedback(null)
    form.resetFields()
  }

  const handleCancel = () => {
    if (isBusy) {
      return
    }

    if (requiresExplicitAbandon || task.status !== 'PENDING') {
      clearActiveIntent(intent)
    }
    closeModal()
  }

  const renderFinalDecision = () => {
    if (task.status === 'PENDING') {
      return (
        <>
          <Typography.Paragraph>
            请分别选择批准或驳回。服务端会重新校验任务归属、任务与申请状态、当前版本和幂等键。
          </Typography.Paragraph>
          <Space wrap>
            <Button type={'primary'} onClick={() => handleOpen('APPROVED')}>
              批准申请
            </Button>
            <Button danger onClick={() => handleOpen('REJECTED')}>
              驳回申请
            </Button>
          </Space>
        </>
      )
    }

    if (!isTerminal) {
      return (
        <Alert
          type={'warning'}
          showIcon
          title={'未知任务状态不提供决定操作'}
          description={'页面不会根据未知状态猜测任务是否可以批准或驳回。'}
        />
      )
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

    const finalDecision = decisionQuery.data
    if (
      finalDecision.procurementRequestId !== request.id ||
      finalDecision.approvalTaskId !== task.id
    ) {
      return (
        <Alert
          type={'error'}
          showIcon
          title={'最终决定关联的任务或申请不一致'}
          description={'为避免展示其他资源的审批事实，本次响应已被隐藏。'}
        />
      )
    }

    return (
      <FinalApprovalDecisionDetails
        decision={finalDecision}
        expectedStatus={task.status}
        statusFactLabel={'任务状态'}
      />
    )
  }

  return (
    <>
      {renderFinalDecision()}
      <Modal
        title={
          result
            ? '审批决定已保存'
            : mode === 'REJECTED'
              ? '确认驳回采购申请'
              : '确认批准采购申请'
        }
        open={mode !== null}
        okText={
          result
            ? '关闭'
            : retrying
              ? '使用原幂等键重试'
              : mode === 'REJECTED'
                ? '确认驳回'
                : '确认批准'
        }
        cancelText={requiresExplicitAbandon ? '放弃本次决定意图' : '取消'}
        confirmLoading={isBusy}
        closable={!isBusy && !requiresExplicitAbandon}
        mask={{ closable: !isBusy && !requiresExplicitAbandon }}
        keyboard={!isBusy && !requiresExplicitAbandon}
        okButtonProps={{
          danger: mode === 'REJECTED' && !result,
          disabled: isBusy || (!result && !canRetry),
        }}
        cancelButtonProps={{
          disabled: isBusy,
          style: result ? { display: 'none' } : undefined,
        }}
        onCancel={handleCancel}
        onOk={result ? closeModal : () => void handleSubmit()}
      >
        {result ? (
          <Result
            status={'success'}
            title={result.decision === 'APPROVED' ? '申请已批准' : '申请已驳回'}
            subTitle={'以下内容来自服务端已保存的唯一审批决定。'}
          >
            <Descriptions
              bordered
              column={{ xs: 1, sm: 1 }}
              items={[
                {
                  key: 'decision',
                  label: '决定',
                  children: result.decision,
                },
                {
                  key: 'actorId',
                  label: '操作人',
                  children: result.actorId,
                },
                {
                  key: 'decidedAt',
                  label: '决定时间',
                  children: formatDateTime(result.decidedAt),
                },
                {
                  key: 'comment',
                  label: '审批意见',
                  children: result.comment || '无',
                },
                {
                  key: 'approvalTaskVersion',
                  label: '任务新版本',
                  children: result.approvalTaskVersion,
                },
              ]}
            />
          </Result>
        ) : (
          <>
            <Alert
              className={'page-alert'}
              type={mode === 'REJECTED' ? 'warning' : 'info'}
              showIcon
              title={
                mode === 'REJECTED'
                  ? '驳回会形成不可撤销的 R1 最终决定'
                  : '批准会形成不可撤销的 R1 最终决定'
              }
              description={`任务 ${task.id} 当前服务端版本为 ${task.version}。`}
            />
            <Form form={form} layout={'vertical'}>
              <Form.Item
                name={'comment'}
                label={mode === 'REJECTED' ? '驳回原因' : '批准意见（可选）'}
                rules={[
                  {
                    required: mode === 'REJECTED',
                    whitespace: mode === 'REJECTED',
                    message: '请输入驳回原因',
                  },
                  { max: 2000, message: '审批意见不能超过 2000 个字符' },
                ]}
              >
                <Input.TextArea
                  rows={5}
                  maxLength={2000}
                  showCount
                  disabled={intent !== null || isBusy}
                  placeholder={
                    mode === 'REJECTED'
                      ? '请说明驳回原因'
                      : '可填写批准依据或补充说明'
                  }
                />
              </Form.Item>
            </Form>
            {feedback ? (
              <Alert
                className={'page-alert'}
                type={
                  feedback.kind === 'in-progress' ||
                  feedback.kind === 'validation'
                    ? 'info'
                    : 'error'
                }
                showIcon
                title={feedback.title}
                description={feedback.description}
              />
            ) : null}
            {retrying ? (
              <Typography.Paragraph type={'secondary'}>
                重试会复用原决定、原意见、原任务版本和原幂等键；页面不会自动重试写请求。
              </Typography.Paragraph>
            ) : null}
          </>
        )}
      </Modal>
    </>
  )
}
