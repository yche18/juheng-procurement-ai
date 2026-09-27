interface AuditCodeDisplay {
  known: boolean
  label: string
}

const actionLabels: Record<string, string> = {
  PROCUREMENT_REQUEST_CREATED: '创建采购申请',
  PROCUREMENT_REQUEST_UPDATED: '修改采购申请',
  PROCUREMENT_REQUEST_SUBMITTED: '提交采购申请',
  APPROVAL_TASK_ASSIGNED: '分配审批任务',
  APPROVAL_TASK_APPROVED: '批准采购申请',
  APPROVAL_TASK_REJECTED: '驳回采购申请',
}

const targetTypeLabels: Record<string, string> = {
  PROCUREMENT_REQUEST: '采购申请',
  APPROVAL_TASK: '审批任务',
}

export function getAuditActionDisplay(action: string): AuditCodeDisplay {
  const label = actionLabels[action]
  return label
    ? { known: true, label: `${label}（${action}）` }
    : { known: false, label: `未知动作（${action}）` }
}

export function getAuditTargetTypeDisplay(
  targetType: string,
): AuditCodeDisplay {
  const label = targetTypeLabels[targetType]
  return label
    ? { known: true, label: `${label}（${targetType}）` }
    : { known: false, label: `未知目标类型（${targetType}）` }
}

export function getAuditResultDisplay(result: string): AuditCodeDisplay {
  return result === 'SUCCESS'
    ? { known: true, label: '成功（SUCCESS）' }
    : { known: false, label: `未知结果（${result}）` }
}
