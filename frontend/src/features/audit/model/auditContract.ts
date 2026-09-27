import type {
  AuditEventResponse,
  ProcurementAuditTrailResponse,
} from '../types/audit'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function hasString(value: Record<string, unknown>, key: string): boolean {
  return typeof value[key] === 'string'
}

function isAuditEvent(value: unknown): value is AuditEventResponse {
  if (!isRecord(value)) {
    return false
  }

  return (
    hasString(value, 'id') &&
    hasString(value, 'actorId') &&
    hasString(value, 'action') &&
    hasString(value, 'targetType') &&
    hasString(value, 'targetId') &&
    hasString(value, 'timestamp') &&
    hasString(value, 'result') &&
    hasString(value, 'requestIdentifier')
  )
}

export function isProcurementAuditTrail(
  value: unknown,
): value is ProcurementAuditTrailResponse {
  if (!isRecord(value) || !Array.isArray(value.events)) {
    return false
  }

  return (
    hasString(value, 'procurementRequestId') &&
    value.events.every(isAuditEvent)
  )
}
