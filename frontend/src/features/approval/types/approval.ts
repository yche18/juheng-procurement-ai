import type {
  PageResponse,
  ProcurementRequestDetailResponse,
} from '../../procurement/public'

export const approvalTaskStatuses = [
  'PENDING',
  'APPROVED',
  'REJECTED',
] as const

export type ApprovalTaskStatus = (typeof approvalTaskStatuses)[number]

export type ApprovalDecision = 'APPROVED' | 'REJECTED'

export interface ApprovalTaskProcurementRequestSummary {
  id: string
  businessNumber: string
  creatorId: string
  title: string
  department: string
  expectedDeliveryDate: string
  currency: string
  estimatedTotal: number
  status: string
  version: number
}

export interface ApprovalTaskSummaryResponse {
  id: string
  status: string
  version: number
  createdAt: string
  updatedAt: string
  procurementRequest: ApprovalTaskProcurementRequestSummary
}

export interface ApprovalTaskDetailResponse {
  id: string
  assigneeId: string
  status: string
  version: number
  createdAt: string
  updatedAt: string
  procurementRequest: ProcurementRequestDetailResponse
}

export interface ApprovalTaskListQuery {
  page: number
  size: number
  status?: ApprovalTaskStatus
}

export interface ApprovalDecisionResponse {
  approvalTaskId: string
  approvalTaskStatus: ApprovalDecision
  approvalTaskVersion: number
  decisionId: string
  decision: ApprovalDecision
  actorId: string
  decidedAt: string
  comment: string | null
}

export interface DecideApprovalTaskMutationArgs {
  taskId: string
  requestId: string
  approvalTaskVersion: number
  comment: string | null
  idempotencyKey: string
}

export type ApprovalTaskPageResponse =
  PageResponse<ApprovalTaskSummaryResponse>
