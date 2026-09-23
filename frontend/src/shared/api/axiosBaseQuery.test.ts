import type { BaseQueryApi } from '@reduxjs/toolkit/query'
import { describe, expect, it, vi } from 'vitest'

import { apiClient } from './apiClient'
import { axiosBaseQuery } from './axiosBaseQuery'

function createBaseQueryApi(signal: AbortSignal): BaseQueryApi {
  return {
    signal,
    abort: vi.fn(),
    dispatch: vi.fn(),
    getState: vi.fn(),
    extra: undefined,
    endpoint: 'testEndpoint',
    type: 'query',
    forced: false,
    queryCacheKey: 'test-key',
  }
}

describe('axiosBaseQuery', () => {
  it('passes the RTK Query AbortSignal to Axios', async () => {
    const controller = new AbortController()
    const requestSpy = vi.spyOn(apiClient, 'request').mockResolvedValue({
      data: { userId: 'demo-requester', roles: ['REQUESTER'] },
    })

    const result = await axiosBaseQuery()(
      { url: 'current-user', method: 'GET' },
      createBaseQueryApi(controller.signal),
      {},
    )

    expect(result).toEqual({
      data: { userId: 'demo-requester', roles: ['REQUESTER'] },
    })
    expect(requestSpy).toHaveBeenCalledWith(
      expect.objectContaining({ signal: controller.signal }),
    )
  })
})
