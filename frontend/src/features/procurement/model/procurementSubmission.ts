export interface ProcurementSubmissionIntent {
  requestId: string
  version: number
  idempotencyKey: string
}

const STORAGE_PREFIX = 'juheng.procurement.submit-intent.'

function storageKey(requestId: string): string {
  return `${STORAGE_PREFIX}${requestId}`
}

function isSubmissionIntent(
  value: unknown,
): value is ProcurementSubmissionIntent {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.requestId === 'string' &&
    typeof candidate.version === 'number' &&
    Number.isInteger(candidate.version) &&
    candidate.version >= 0 &&
    typeof candidate.idempotencyKey === 'string' &&
    candidate.idempotencyKey.length > 0 &&
    candidate.idempotencyKey.length <= 64
  )
}

function loadStoredIntent(requestId: string): ProcurementSubmissionIntent | null {
  try {
    const serialized = window.sessionStorage.getItem(storageKey(requestId))
    if (!serialized) {
      return null
    }

    const parsed: unknown = JSON.parse(serialized)
    if (!isSubmissionIntent(parsed) || parsed.requestId !== requestId) {
      window.sessionStorage.removeItem(storageKey(requestId))
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function storeIntent(intent: ProcurementSubmissionIntent): void {
  try {
    window.sessionStorage.setItem(
      storageKey(intent.requestId),
      JSON.stringify(intent),
    )
  } catch {
    // 页面内状态仍会复用该键；浏览器拒绝存储时只失去刷新恢复能力。
  }
}

export function getOrCreateSubmissionIntent(
  requestId: string,
  version: number,
): ProcurementSubmissionIntent {
  const stored = loadStoredIntent(requestId)
  if (stored?.version === version) {
    return stored
  }

  if (stored) {
    clearSubmissionIntent(stored)
  }

  const intent = {
    requestId,
    version,
    idempotencyKey: crypto.randomUUID(),
  }
  storeIntent(intent)
  return intent
}

export function clearSubmissionIntent(
  intent: ProcurementSubmissionIntent,
): void {
  try {
    const stored = loadStoredIntent(intent.requestId)
    if (stored?.idempotencyKey === intent.idempotencyKey) {
      window.sessionStorage.removeItem(storageKey(intent.requestId))
    }
  } catch {
    // 存储不可用时无须额外处理，页面内状态由调用方清理。
  }
}
