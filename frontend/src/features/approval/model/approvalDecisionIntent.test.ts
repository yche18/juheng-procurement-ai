import { beforeEach, describe, expect, it } from 'vitest'

import {
  clearApprovalDecisionIntent,
  normalizeApprovalDecisionComment,
  resolveApprovalDecisionIntent,
} from './approvalDecisionIntent'

const taskId = '30000000-0000-0000-0000-000000000001'

describe('approvalDecisionIntent', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('normalizes optional and required decision comments consistently', () => {
    expect(normalizeApprovalDecisionComment(undefined)).toBeNull()
    expect(normalizeApprovalDecisionComment('   ')).toBeNull()
    expect(normalizeApprovalDecisionComment('  依据充分  ')).toBe('依据充分')
  })

  it('restores the same key for an identical decision without storing comment text', async () => {
    const input = {
      taskId,
      approvalTaskVersion: 2,
      decision: 'APPROVED' as const,
      comment: '同意本次采购',
    }

    const first = await resolveApprovalDecisionIntent(input)
    const restored = await resolveApprovalDecisionIntent(input)

    expect(first.kind).toBe('ready')
    expect(restored.kind).toBe('ready')
    if (first.kind === 'ready' && restored.kind === 'ready') {
      expect(restored.intent.idempotencyKey).toBe(first.intent.idempotencyKey)
      expect(restored.intent.idempotencyKey.length).toBeLessThanOrEqual(64)
    }
    expect(window.sessionStorage.getItem(`juheng.approval.decision-intent.${taskId}`))
      .not.toContain('同意本次采购')
  })

  it('requires explicit abandonment before changing the stored payload', async () => {
    const first = await resolveApprovalDecisionIntent({
      taskId,
      approvalTaskVersion: 2,
      decision: 'APPROVED',
      comment: null,
    })
    const conflicting = await resolveApprovalDecisionIntent({
      taskId,
      approvalTaskVersion: 2,
      decision: 'REJECTED',
      comment: '预算依据不足',
    })

    expect(conflicting).toEqual({
      kind: 'conflict',
      existingDecision: 'APPROVED',
      existingVersion: 2,
    })

    clearApprovalDecisionIntent(taskId)
    const replacement = await resolveApprovalDecisionIntent({
      taskId,
      approvalTaskVersion: 2,
      decision: 'REJECTED',
      comment: '预算依据不足',
    })
    expect(replacement.kind).toBe('ready')
    if (first.kind === 'ready' && replacement.kind === 'ready') {
      expect(replacement.intent.idempotencyKey).not.toBe(
        first.intent.idempotencyKey,
      )
    }
  })
})
