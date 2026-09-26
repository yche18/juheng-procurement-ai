export const categoryCodes = [
  'LAPTOP',
  'MONITOR',
  'OFFICE_CHAIR',
  'SOFTWARE_LICENSE',
] as const

export type CategoryCode = (typeof categoryCodes)[number]

export const procurementRequestStatuses = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'REJECTED',
] as const

export type ProcurementRequestStatus =
  (typeof procurementRequestStatuses)[number]

export type ApprovalDecision = 'APPROVED' | 'REJECTED'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ProcurementItemWrite {
  name: string
  categoryCode: CategoryCode
  specification: string
  quantity: number
  unit: string
  estimatedUnitPrice: number
}

export interface CreateProcurementRequestRequest {
  title: string
  purpose: string
  department: string
  expectedDeliveryDate: string
  items: ProcurementItemWrite[]
}

export interface UpdateProcurementRequestRequest
  extends CreateProcurementRequestRequest {
  version: number
}

export interface UpdateProcurementRequestMutationArgs {
  requestId: string
  body: UpdateProcurementRequestRequest
}

export interface ProcurementItemResponse {
  id: string
  lineNumber: number
  name: string
  categoryCode: string
  specification: string
  quantity: number
  unit: string
  estimatedUnitPrice: number
  estimatedLineTotal: number
}

export interface CreateProcurementRequestResponse {
  id: string
  businessNumber: string
  creatorId: string
  title: string
  purpose: string
  department: string
  expectedDeliveryDate: string
  currency: 'CNY'
  estimatedTotal: number
  status: 'DRAFT'
  version: number
  createdAt: string
  items: ProcurementItemResponse[]
}

export interface ProcurementRequestSummaryResponse {
  id: string
  businessNumber: string
  title: string
  department: string
  expectedDeliveryDate: string
  currency: string
  estimatedTotal: number
  status: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProcurementRequestDetailResponse {
  id: string
  businessNumber: string
  creatorId: string
  title: string
  purpose: string
  department: string
  expectedDeliveryDate: string
  currency: string
  estimatedTotal: number
  status: string
  version: number
  createdAt: string
  updatedAt: string
  items: ProcurementItemResponse[]
}

export interface FinalApprovalDecisionResponse {
  procurementRequestId: string
  approvalTaskId: string
  decisionId: string
  decision: string
  actorId: string
  decidedAt: string
  comment: string | null
}

export interface ProcurementRequestListQuery {
  page: number
  size: number
  status?: ProcurementRequestStatus
}
