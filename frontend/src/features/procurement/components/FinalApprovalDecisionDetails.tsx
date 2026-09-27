import { Alert, Descriptions, Tag } from 'antd'

import { formatDateTime } from '../../../shared/model/displayFormatters'
import { getDecisionDisplay } from '../model/procurementPresentation'
import type { FinalApprovalDecisionResponse } from '../types/procurement'

interface FinalApprovalDecisionDetailsProps {
  decision: FinalApprovalDecisionResponse
  expectedStatus?: string
  statusFactLabel?: string
}

export function FinalApprovalDecisionDetails({
  decision,
  expectedStatus,
  statusFactLabel = '资源状态',
}: FinalApprovalDecisionDetailsProps) {
  const decisionDisplay = getDecisionDisplay(decision.decision)
  const conflictsWithStatus =
    expectedStatus !== undefined &&
    decisionDisplay.known &&
    decision.decision !== expectedStatus

  return (
    <>
      {conflictsWithStatus ? (
        <Alert
          className={'page-alert'}
          type={'warning'}
          showIcon
          title={`${statusFactLabel}与最终决定不一致`}
          description={'页面分别展示服务端返回的两个事实，不会静默改写其中任何一个。'}
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
