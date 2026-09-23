import type { BaseQueryFn } from '@reduxjs/toolkit/query'
import type { AxiosRequestConfig } from 'axios'

import { normalizeApiError, type FrontendApiError } from './apiError'
import { apiClient } from './apiClient'

export interface AxiosBaseQueryArgs extends Omit<AxiosRequestConfig, 'url'> {
  url: string
}

export function axiosBaseQuery(): BaseQueryFn<
  AxiosBaseQueryArgs,
  unknown,
  FrontendApiError
> {
  return async ({ url, ...config }, baseQueryApi) => {
    try {
      const response = await apiClient.request({
        ...config,
        url,
        signal: baseQueryApi.signal,
      })

      return { data: response.data }
    } catch (error) {
      return { error: normalizeApiError(error) }
    }
  }
}
