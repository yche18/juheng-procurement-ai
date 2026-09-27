import { describe, expect, it } from 'vitest'

import {
  clearSubmissionIntent,
  getOrCreateSubmissionIntent,
} from './procurementSubmission'

const requestId = '10000000-0000-0000-0000-000000000001'

describe('procurementSubmission', () => {
  it('restores the same key for the same request version', () => {
    const first = getOrCreateSubmissionIntent(requestId, 3)
    const restored = getOrCreateSubmissionIntent(requestId, 3)

    expect(first.idempotencyKey).not.toHaveLength(0)
    expect(first.idempotencyKey.length).toBeLessThanOrEqual(64)
    expect(restored).toEqual(first)
  })

  it('creates a new intent when the request version changes', () => {
    const first = getOrCreateSubmissionIntent(requestId, 3)
    const nextVersion = getOrCreateSubmissionIntent(requestId, 4)

    expect(nextVersion.version).toBe(4)
    expect(nextVersion.idempotencyKey).not.toBe(first.idempotencyKey)
  })

  it('only clears the stored record that matches the active key', () => {
    const first = getOrCreateSubmissionIntent(requestId, 3)
    const nextVersion = getOrCreateSubmissionIntent(requestId, 4)

    clearSubmissionIntent(first)
    expect(getOrCreateSubmissionIntent(requestId, 4)).toEqual(nextVersion)

    clearSubmissionIntent(nextVersion)
    expect(getOrCreateSubmissionIntent(requestId, 4).idempotencyKey).not.toBe(
      nextVersion.idempotencyKey,
    )
  })
})
