import axios from 'axios'

import { getAuthorizationHeader } from './credentials'

export const API_TIMEOUT_MS = 10_000

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: API_TIMEOUT_MS,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const authorizationHeader = getAuthorizationHeader()

  if (authorizationHeader) {
    config.headers.set('Authorization', authorizationHeader)
  } else {
    config.headers.delete('Authorization')
  }

  return config
})
