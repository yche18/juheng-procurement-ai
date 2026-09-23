import type { AxiosError } from 'axios'
import { AxiosError as AxiosErrorClass } from 'axios'
import { describe, expect, it } from 'vitest'

import { normalizeApiError } from './apiError'

describe('normalizeApiError', () => {
  it('preserves a valid backend error contract', () => {
    const error = {
      isAxiosError: true,
      response: {
        status: 409,
        data: {
          code: 'BUSINESS_CONFLICT',
          message: 'Request conflicts with the current business state',
          path: '/api/procurement-requests/example/submit',
          fieldErrors: [],
        },
      },
    } as AxiosError

    expect(normalizeApiError(error)).toEqual({
      kind: 'backend',
      status: 409,
      response: error.response?.data,
    })
  })

  it('distinguishes cancellation, timeout, network, and unexpected responses', () => {
    expect(
      normalizeApiError({
        isAxiosError: true,
        code: AxiosErrorClass.ERR_CANCELED,
      }),
    ).toEqual({ kind: 'cancelled' })

    expect(
      normalizeApiError({
        isAxiosError: true,
        code: AxiosErrorClass.ECONNABORTED,
      }),
    ).toMatchObject({ kind: 'timeout' })

    expect(normalizeApiError({ isAxiosError: true })).toMatchObject({
      kind: 'network',
    })

    expect(
      normalizeApiError({
        isAxiosError: true,
        response: { status: 500, data: { message: 'not the contract' } },
      }),
    ).toMatchObject({ kind: 'unexpected' })
  })
})
