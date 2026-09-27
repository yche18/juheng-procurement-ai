import type { ApprovalDecision } from '../types/approval'

export interface ApprovalDecisionIntent {
  taskId: string
  approvalTaskVersion: number
  decision: ApprovalDecision
  comment: string | null
  payloadFingerprint: string
  idempotencyKey: string
}

interface StoredApprovalDecisionIntent {
  taskId: string
  approvalTaskVersion: number
  decision: ApprovalDecision
  payloadFingerprint: string
  idempotencyKey: string
}

export interface ApprovalDecisionIntentInput {
  taskId: string
  approvalTaskVersion: number
  decision: ApprovalDecision
  comment: string | null
}

export type ApprovalDecisionIntentResolution =
  | { kind: 'ready'; intent: ApprovalDecisionIntent }
  | {
      kind: 'conflict'
      existingDecision: ApprovalDecision
      existingVersion: number
    }

const STORAGE_PREFIX = 'juheng.approval.decision-intent.'

function storageKey(taskId: string): string {
  return `${STORAGE_PREFIX}${taskId}`
}

function isApprovalDecision(value: unknown): value is ApprovalDecision {
  return value === 'APPROVED' || value === 'REJECTED'
}

function isStoredIntent(value: unknown): value is StoredApprovalDecisionIntent {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.taskId === 'string' &&
    typeof candidate.approvalTaskVersion === 'number' &&
    Number.isInteger(candidate.approvalTaskVersion) &&
    candidate.approvalTaskVersion >= 0 &&
    isApprovalDecision(candidate.decision) &&
    typeof candidate.payloadFingerprint === 'string' &&
    candidate.payloadFingerprint.length === 64 &&
    typeof candidate.idempotencyKey === 'string' &&
    candidate.idempotencyKey.length > 0 &&
    candidate.idempotencyKey.length <= 64
  )
}

function loadStoredIntent(taskId: string): StoredApprovalDecisionIntent | null {
  try {
    const serialized = window.sessionStorage.getItem(storageKey(taskId))
    if (!serialized) {
      return null
    }

    const parsed: unknown = JSON.parse(serialized)
    if (!isStoredIntent(parsed) || parsed.taskId !== taskId) {
      window.sessionStorage.removeItem(storageKey(taskId))
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function storeIntent(intent: StoredApprovalDecisionIntent): void {
  try {
    window.sessionStorage.setItem(storageKey(intent.taskId), JSON.stringify(intent))
  } catch {
    // 页面内状态仍会复用该键；浏览器拒绝存储时只失去重新挂载后的恢复能力。
  }
}

async function createPayloadFingerprint(
  input: ApprovalDecisionIntentInput,
): Promise<string> {
  const canonicalPayload = JSON.stringify({
    taskId: input.taskId,
    approvalTaskVersion: input.approvalTaskVersion,
    decision: input.decision,
    comment: input.comment,
  })
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(canonicalPayload),
  )
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, '0'),
  ).join('')
}

export function normalizeApprovalDecisionComment(
  comment: string | null | undefined,
): string | null {
  const normalized = comment?.trim() ?? ''
  return normalized.length === 0 ? null : normalized
}

export async function resolveApprovalDecisionIntent(
  input: ApprovalDecisionIntentInput,
): Promise<ApprovalDecisionIntentResolution> {
  const payloadFingerprint = await createPayloadFingerprint(input)
  const stored = loadStoredIntent(input.taskId)

  if (stored) {
    if (
      stored.approvalTaskVersion === input.approvalTaskVersion &&
      stored.decision === input.decision &&
      stored.payloadFingerprint === payloadFingerprint
    ) {
      return {
        kind: 'ready',
        intent: { ...input, payloadFingerprint, idempotencyKey: stored.idempotencyKey },
      }
    }

    return {
      kind: 'conflict',
      existingDecision: stored.decision,
      existingVersion: stored.approvalTaskVersion,
    }
  }

  const intent: ApprovalDecisionIntent = {
    ...input,
    payloadFingerprint,
    idempotencyKey: crypto.randomUUID(),
  }
  storeIntent({
    taskId: intent.taskId,
    approvalTaskVersion: intent.approvalTaskVersion,
    decision: intent.decision,
    payloadFingerprint: intent.payloadFingerprint,
    idempotencyKey: intent.idempotencyKey,
  })
  return { kind: 'ready', intent }
}

export function clearApprovalDecisionIntent(
  taskId: string,
  idempotencyKey?: string,
): void {
  try {
    const stored = loadStoredIntent(taskId)
    if (!idempotencyKey || stored?.idempotencyKey === idempotencyKey) {
      window.sessionStorage.removeItem(storageKey(taskId))
    }
  } catch {
    // 存储不可用时无须额外处理，页面内状态由调用方清理。
  }
}
