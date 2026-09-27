import { isProcurementRequestDetail } from '../../procurement/public'
import type {
  ApprovalTaskDetailResponse,
  ApprovalTaskPageResponse,
  ApprovalTaskProcurementRequestSummary,
  ApprovalTaskSummaryResponse,
} from '../types/approval'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function hasString(value: Record<string, unknown>, key: string): boolean {
  return typeof value[key] === 'string'
}

function hasFiniteNumber(
  value: Record<string, unknown>,
  key: string,
): boolean {
  return typeof value[key] === 'number' && Number.isFinite(value[key])
}

function hasNonNegativeInteger(
  value: Record<string, unknown>,
  key: string,
): boolean {
  const candidate = value[key]
  return (
    typeof candidate === 'number' &&
    Number.isInteger(candidate) &&
    candidate >= 0
  )
}

function isApprovalTaskRequestSummary(
  value: unknown,
): value is ApprovalTaskProcurementRequestSummary {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasString(value, 'businessNumber') &&
    hasString(value, 'creatorId') &&
    hasString(value, 'title') &&
    hasString(value, 'department') &&
    hasString(value, 'expectedDeliveryDate') &&
    hasString(value, 'currency') &&
    hasFiniteNumber(value, 'estimatedTotal') &&
    hasString(value, 'status') &&
    hasNonNegativeInteger(value, 'version')
  )
}

function isApprovalTaskSummary(
  value: unknown,
): value is ApprovalTaskSummaryResponse {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasString(value, 'status') &&
    hasNonNegativeInteger(value, 'version') &&
    hasString(value, 'createdAt') &&
    hasString(value, 'updatedAt') &&
    isApprovalTaskRequestSummary(value.procurementRequest)
  )
}

export function isApprovalTaskPage(
  value: unknown,
): value is ApprovalTaskPageResponse {
  if (!isRecord(value) || !Array.isArray(value.content)) {
    return false
  }

  return (
    value.content.every(isApprovalTaskSummary) &&
    hasNonNegativeInteger(value, 'page') &&
    hasNonNegativeInteger(value, 'size') &&
    hasNonNegativeInteger(value, 'totalElements') &&
    hasNonNegativeInteger(value, 'totalPages')
  )
}

export function isApprovalTaskDetail(
  value: unknown,
): value is ApprovalTaskDetailResponse {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasString(value, 'assigneeId') &&
    hasString(value, 'status') &&
    hasNonNegativeInteger(value, 'version') &&
    hasString(value, 'createdAt') &&
    hasString(value, 'updatedAt') &&
    isProcurementRequestDetail(value.procurementRequest)
  )
}
