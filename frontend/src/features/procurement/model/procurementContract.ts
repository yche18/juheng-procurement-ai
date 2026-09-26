import type {
  FinalApprovalDecisionResponse,
  PageResponse,
  ProcurementItemResponse,
  ProcurementRequestDetailResponse,
  ProcurementRequestSummaryResponse,
} from '../types/procurement'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function hasString(value: Record<string, unknown>, key: string): boolean {
  return typeof value[key] === 'string'
}

function hasNumber(value: Record<string, unknown>, key: string): boolean {
  return typeof value[key] === 'number' && Number.isFinite(value[key])
}

function isProcurementItem(value: unknown): value is ProcurementItemResponse {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasNumber(value, 'lineNumber') &&
    hasString(value, 'name') &&
    hasString(value, 'categoryCode') &&
    hasString(value, 'specification') &&
    hasNumber(value, 'quantity') &&
    hasString(value, 'unit') &&
    hasNumber(value, 'estimatedUnitPrice') &&
    hasNumber(value, 'estimatedLineTotal')
  )
}

export function isProcurementRequestSummary(
  value: unknown,
): value is ProcurementRequestSummaryResponse {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasString(value, 'businessNumber') &&
    hasString(value, 'title') &&
    hasString(value, 'department') &&
    hasString(value, 'expectedDeliveryDate') &&
    hasString(value, 'currency') &&
    hasNumber(value, 'estimatedTotal') &&
    hasString(value, 'status') &&
    hasNumber(value, 'version') &&
    hasString(value, 'createdAt') &&
    hasString(value, 'updatedAt')
  )
}

export function isProcurementRequestPage(
  value: unknown,
): value is PageResponse<ProcurementRequestSummaryResponse> {
  if (!isRecord(value) || !Array.isArray(value.content)) {
    return false
  }

  return (
    value.content.every(isProcurementRequestSummary) &&
    hasNumber(value, 'page') &&
    hasNumber(value, 'size') &&
    hasNumber(value, 'totalElements') &&
    hasNumber(value, 'totalPages')
  )
}

export function isProcurementRequestDetail(
  value: unknown,
): value is ProcurementRequestDetailResponse {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasString(value, 'businessNumber') &&
    hasString(value, 'creatorId') &&
    hasString(value, 'title') &&
    hasString(value, 'purpose') &&
    hasString(value, 'department') &&
    hasString(value, 'expectedDeliveryDate') &&
    hasString(value, 'currency') &&
    hasNumber(value, 'estimatedTotal') &&
    hasString(value, 'status') &&
    hasNumber(value, 'version') &&
    hasString(value, 'createdAt') &&
    hasString(value, 'updatedAt') &&
    value.items.every(isProcurementItem)
  )
}

export function isFinalApprovalDecision(
  value: unknown,
): value is FinalApprovalDecisionResponse {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'procurementRequestId') &&
    hasString(value, 'approvalTaskId') &&
    hasString(value, 'decisionId') &&
    hasString(value, 'actorId') &&
    hasString(value, 'decision') &&
    hasString(value, 'decidedAt') &&
    (typeof value.comment === 'string' || value.comment === null)
  )
}
