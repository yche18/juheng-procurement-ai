import type { TagProps } from 'antd'

import {
  approvalTaskStatuses,
  type ApprovalTaskStatus,
} from '../types/approval'

export const approvalTaskStatusLabels: Record<ApprovalTaskStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
}

const approvalTaskStatusColors: Record<
  ApprovalTaskStatus,
  TagProps['color']
> = {
  PENDING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}

export function isApprovalTaskStatus(
  value: string,
): value is ApprovalTaskStatus {
  return approvalTaskStatuses.some((status) => status === value)
}

export function getApprovalTaskStatusDisplay(status: string): {
  label: string
  color: TagProps['color']
  known: boolean
} {
  if (isApprovalTaskStatus(status)) {
    return {
      label: `${approvalTaskStatusLabels[status]}（${status}）`,
      color: approvalTaskStatusColors[status],
      known: true,
    }
  }

  return {
    label: `未知状态（${status || '空值'}）`,
    color: 'warning',
    known: false,
  }
}
