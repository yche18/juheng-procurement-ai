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
