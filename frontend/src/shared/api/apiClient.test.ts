import type {
  AxiosAdapter,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import { describe, expect, it } from 'vitest'

import { apiClient, API_TIMEOUT_MS } from './apiClient'
import { clearCredentials, setCredentials } from './credentials'

function successfulAdapter(
  onRequest: (config: InternalAxiosRequestConfig) => void,
): AxiosAdapter {
  return (config) => {
    onRequest(config)

    return Promise.resolve({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    } satisfies AxiosResponse)
  }
}

describe('apiClient', () => {
  it('uses the bounded timeout from the shared client', () => {
    expect(apiClient.defaults.timeout).toBe(API_TIMEOUT_MS)
  })

  it('adds and removes the in-memory Basic Authorization header', async () => {
    let lastConfig: InternalAxiosRequestConfig | undefined
    const adapter = successfulAdapter((config) => {
      lastConfig = config
    })

    setCredentials('demo-requester', 'juheng-local')
    await apiClient.get('current-user', { adapter })

    expect(lastConfig?.headers.get('Authorization')).toBe(
      `Basic ${btoa('demo-requester:juheng-local')}`,
    )

    clearCredentials()
    await apiClient.get('current-user', { adapter })

    expect(lastConfig?.headers.get('Authorization')).toBeUndefined()
  })
})
