import type { TagProps } from 'antd'

import {
  categoryCodes,
  procurementRequestStatuses,
  type CategoryCode,
  type ProcurementRequestStatus,
} from '../types/procurement'

export const categoryLabels: Record<CategoryCode, string> = {
  LAPTOP: '笔记本电脑',
  MONITOR: '显示器',
  OFFICE_CHAIR: '办公椅',
  SOFTWARE_LICENSE: '软件许可',
}

export const procurementStatusLabels: Record<ProcurementRequestStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
}

const procurementStatusColors: Record<
  ProcurementRequestStatus,
  TagProps['color']
> = {
  DRAFT: 'default',
  SUBMITTED: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}

export function isProcurementRequestStatus(
  value: string,
): value is ProcurementRequestStatus {
  return procurementRequestStatuses.some((status) => status === value)
}

export function isKnownCategoryCode(value: string): value is CategoryCode {
  return categoryCodes.some((code) => code === value)
}

export function getProcurementStatusDisplay(status: string): {
  label: string
  color: TagProps['color']
  known: boolean
} {
  if (isProcurementRequestStatus(status)) {
    return {
      label: `${procurementStatusLabels[status]}（${status}）`,
      color: procurementStatusColors[status],
      known: true,
    }
  }

  return {
    label: `未知状态（${status || '空值'}）`,
    color: 'warning',
    known: false,
  }
}

export function getDecisionDisplay(decision: string): {
  label: string
  color: TagProps['color']
  known: boolean
} {
  if (decision === 'APPROVED') {
    return { label: '已批准（APPROVED）', color: 'success', known: true }
  }
  if (decision === 'REJECTED') {
    return { label: '已驳回（REJECTED）', color: 'error', known: true }
  }

  return {
    label: `未知决定（${decision || '空值'}）`,
    color: 'warning',
    known: false,
  }
}

export function getCategoryDisplay(categoryCode: string): string {
  return isKnownCategoryCode(categoryCode)
    ? `${categoryLabels[categoryCode]}（${categoryCode}）`
    : `未知品类（${categoryCode || '空值'}）`
}

export function formatMoney(value: number, currency: string): string {
  if (currency !== 'CNY') {
    return `${value.toLocaleString('zh-CN')} ${currency || '空值'}（未知币种）`
  }

  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(value)
}

export function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return `无效时间（${value}）`
  }

  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}
