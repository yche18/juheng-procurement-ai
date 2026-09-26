import { AxiosError, isAxiosError } from 'axios'

export interface FieldViolation {
  field: string
  code: string
  message: string
}

export interface ApiErrorResponse {
  code: string
  message: string
  path: string
  fieldErrors: FieldViolation[]
}

export type FrontendApiError =
  | { kind: 'backend'; status: number; response: ApiErrorResponse }
  | { kind: 'network'; message: string }
  | { kind: 'timeout'; message: string }
  | { kind: 'cancelled' }
  | { kind: 'unexpected'; message: string }

function isFieldViolation(value: unknown): value is FieldViolation {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.field === 'string' &&
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string'
  )
}

export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.path === 'string' &&
    Array.isArray(candidate.fieldErrors) &&
    candidate.fieldErrors.every(isFieldViolation)
  )
}

export function normalizeApiError(error: unknown): FrontendApiError {
  if (!isAxiosError(error)) {
    return {
      kind: 'unexpected',
      message: '发生了无法识别的客户端错误。',
    }
  }

  if (error.code === AxiosError.ERR_CANCELED) {
    return { kind: 'cancelled' }
  }

  if (error.code === AxiosError.ECONNABORTED || error.code === 'ETIMEDOUT') {
    return {
      kind: 'timeout',
      message: '请求超时，请检查服务状态后重试。',
    }
  }

  if (error.response && isApiErrorResponse(error.response.data)) {
    return {
      kind: 'backend',
      status: error.response.status,
      response: error.response.data,
    }
  }

  if (!error.response) {
    return {
      kind: 'network',
      message: '无法连接服务，请检查网络和后端服务状态。',
    }
  }

  return {
    kind: 'unexpected',
    message: '服务返回了无法识别的错误响应。',
  }
}

export function isAuthenticationRequiredError(
  error: unknown,
): error is Extract<FrontendApiError, { kind: 'backend' }> {
  return (
    typeof error === 'object' &&
    error !== null &&
    'kind' in error &&
    error.kind === 'backend' &&
    'response' in error &&
    isApiErrorResponse(error.response) &&
    error.response.code === 'AUTHENTICATION_REQUIRED'
  )
}

export function isBackendErrorCode(error: unknown, code: string): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'kind' in error &&
    error.kind === 'backend' &&
    'response' in error &&
    isApiErrorResponse(error.response) &&
    error.response.code === code
  )
}

export function getApiErrorMessage(error: unknown): string {
  if (typeof error !== 'object' || error === null || !('kind' in error)) {
    return '操作失败，请稍后重试。'
  }

  if (error.kind === 'cancelled') {
    return ''
  }

  if (error.kind === 'backend' && 'response' in error) {
    if (isApiErrorResponse(error.response)) {
      return error.response.message
    }
  }

  if (
    (error.kind === 'network' ||
      error.kind === 'timeout' ||
      error.kind === 'unexpected') &&
    'message' in error &&
    typeof error.message === 'string'
  ) {
    return error.message
  }

  return '操作失败，请稍后重试。'
}
