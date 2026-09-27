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

export type ApprovalTaskPageResponse =
  PageResponse<ApprovalTaskSummaryResponse>
