import { Tag } from 'antd'

import { getApprovalTaskStatusDisplay } from '../model/approvalPresentation'

interface ApprovalTaskStatusTagProps {
  status: string
}

export function ApprovalTaskStatusTag({
  status,
}: ApprovalTaskStatusTagProps) {
  const display = getApprovalTaskStatusDisplay(status)
  return <Tag color={display.color}>{display.label}</Tag>
}
