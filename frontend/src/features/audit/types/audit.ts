export interface AuditEventResponse {
  id: string
  actorId: string
  action: string
  targetType: string
  targetId: string
  timestamp: string
  result: string
  requestIdentifier: string
}

export interface ProcurementAuditTrailResponse {
  procurementRequestId: string
  events: AuditEventResponse[]
}
