import {
  isApiErrorResponse,
  type FieldViolation,
  type FrontendApiError,
} from '../../../shared/api/apiError'
import { isKnownCategoryCode } from './procurementPresentation'
import type {
  CategoryCode,
  CreateProcurementRequestRequest,
  ProcurementRequestDetailResponse,
  UpdateProcurementRequestRequest,
} from '../types/procurement'

export interface ProcurementItemFormValue {
  name: string
  categoryCode?: CategoryCode
  specification: string
  quantity: string
  unit: string
  estimatedUnitPrice: string
}

export interface ProcurementRequestFormValue {
  title: string
  purpose: string
  department: string
  expectedDeliveryDate: string
  items: ProcurementItemFormValue[]
}

export type ProcurementFormFieldName =
  | keyof ProcurementRequestFormValue
  | ['items', number, keyof ProcurementItemFormValue]

export interface LocatedProcurementFormError {
  name: ProcurementFormFieldName
  errors: string[]
}

export const categoryLabels: Record<CategoryCode, string> = {
  LAPTOP: '笔记本电脑',
  MONITOR: '显示器',
  OFFICE_CHAIR: '办公椅',
  SOFTWARE_LICENSE: '软件许可',
}

export function emptyItem(): ProcurementItemFormValue {
  return {
    name: '',
    specification: '',
    quantity: '',
    unit: '',
    estimatedUnitPrice: '',
  }
}

export function initialFormValue(): ProcurementRequestFormValue {
  return {
    title: '',
    purpose: '',
    department: '',
    expectedDeliveryDate: '',
    items: [emptyItem()],
  }
}

export function formatCny(value: number): string {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(value)
}

export function decimalValidator(
  label: string,
  integerDigits: number,
  fractionDigits: number,
  mustBePositive: boolean,
) {
  return (_rule: unknown, value: unknown) => {
    if (value === undefined || value === null || value === '') {
      return Promise.resolve()
    }

    if (typeof value !== 'string' && typeof value !== 'number') {
      return Promise.reject(new Error(`${label}格式不正确`))
    }

    const text = typeof value === 'number' ? value.toString() : value.trim()
    if (!/^\d+(?:\.\d+)?$/.test(text)) {
      return Promise.reject(new Error(`${label}必须是普通十进制数`))
    }

    const [integerPart, fractionPart = ''] = text.split('.')
    const significantInteger = integerPart.replace(/^0+(?=\d)/, '')
    if (
      significantInteger.length > integerDigits ||
      fractionPart.length > fractionDigits
    ) {
      return Promise.reject(
        new Error(
          `${label}最多 ${integerDigits} 位整数和 ${fractionDigits} 位小数`,
        ),
      )
    }

    const numberValue = Number(text)
    if (!Number.isFinite(numberValue)) {
      return Promise.reject(new Error(`${label}格式不正确`))
    }
    if (mustBePositive ? numberValue <= 0 : numberValue < 0) {
      return Promise.reject(
        new Error(mustBePositive ? `${label}必须大于 0` : `${label}不能小于 0`),
      )
    }

    return Promise.resolve()
  }
}

export function toRequest(
  values: ProcurementRequestFormValue,
): CreateProcurementRequestRequest {
  return {
    title: values.title.trim(),
    purpose: values.purpose.trim(),
    department: values.department.trim(),
    expectedDeliveryDate: values.expectedDeliveryDate,
    items: values.items.map((item) => ({
      name: item.name.trim(),
      categoryCode: item.categoryCode as CategoryCode,
      specification: item.specification.trim(),
      quantity: Number(item.quantity),
      unit: item.unit.trim(),
      estimatedUnitPrice: Number(item.estimatedUnitPrice),
    })),
  }
}

export function toUpdateRequest(
  values: ProcurementRequestFormValue,
  version: number,
): UpdateProcurementRequestRequest {
  return {
    version,
    ...toRequest(values),
  }
}

export function toEditableFormValue(
  request: ProcurementRequestDetailResponse,
): ProcurementRequestFormValue | null {
  if (!request.items.every((item) => isKnownCategoryCode(item.categoryCode))) {
    return null
  }

  return {
    title: request.title,
    purpose: request.purpose,
    department: request.department,
    expectedDeliveryDate: request.expectedDeliveryDate,
    items: request.items.map((item) => ({
      name: item.name,
      categoryCode: item.categoryCode as CategoryCode,
      specification: item.specification,
      quantity: String(item.quantity),
      unit: item.unit,
      estimatedUnitPrice: String(item.estimatedUnitPrice),
    })),
  }
}

export function partitionProcurementFormErrors(
  violations: FieldViolation[],
  itemCount: number,
): {
  locatedErrors: LocatedProcurementFormError[]
  unlocatedMessages: string[]
} {
  const locatedErrors: LocatedProcurementFormError[] = []
  const unlocatedMessages: string[] = []

  violations.forEach((violation) => {
    const name = toFormFieldName(violation.field, itemCount)
    if (name) {
      locatedErrors.push({ name, errors: [violation.message] })
    } else {
      unlocatedMessages.push(violation.message)
    }
  })

  return { locatedErrors, unlocatedMessages }
}

export function asBackendError(
  error: unknown,
): Extract<FrontendApiError, { kind: 'backend' }> | null {
  if (typeof error !== 'object' || error === null) {
    return null
  }

  const candidate = error as {
    kind?: unknown
    status?: unknown
    response?: unknown
  }
  if (
    candidate.kind !== 'backend' ||
    typeof candidate.status !== 'number' ||
    !isApiErrorResponse(candidate.response)
  ) {
    return null
  }

  return candidate as Extract<FrontendApiError, { kind: 'backend' }>
}

export function toFormFieldName(
  field: string,
  itemCount: number,
): ProcurementFormFieldName | null {
  if (
    field === 'title' ||
    field === 'purpose' ||
    field === 'department' ||
    field === 'expectedDeliveryDate' ||
    field === 'items'
  ) {
    return field
  }

  const match = /^items\[(\d+)]\.(name|categoryCode|specification|quantity|unit|estimatedUnitPrice)$/.exec(
    field,
  )
  if (!match) {
    return null
  }

  const index = Number(match[1])
  if (index >= itemCount) {
    return null
  }

  const itemField = match[2]
  if (
    itemField !== 'name' &&
    itemField !== 'categoryCode' &&
    itemField !== 'specification' &&
    itemField !== 'quantity' &&
    itemField !== 'unit' &&
    itemField !== 'estimatedUnitPrice'
  ) {
    return null
  }

  return ['items', index, itemField]
}
