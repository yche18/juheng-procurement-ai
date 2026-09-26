export const categoryCodes = [
  'LAPTOP',
  'MONITOR',
  'OFFICE_CHAIR',
  'SOFTWARE_LICENSE',
] as const

export type CategoryCode = (typeof categoryCodes)[number]

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

export interface ProcurementItemResponse {
  id: string
  lineNumber: number
  name: string
  categoryCode: CategoryCode
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
