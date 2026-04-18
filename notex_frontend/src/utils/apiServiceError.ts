export interface ApiServiceError extends Error {
  code: 'INTERNAL_SERVER_ERROR'
  detail: string
}

export const createApiServiceError = (detail: string) => {
  const error = new Error('API Service Error') as ApiServiceError
  error.code = 'INTERNAL_SERVER_ERROR'
  error.detail = detail
  return error
}

export const isApiServiceError = (error: unknown): error is ApiServiceError => {
  if (!error || typeof error !== 'object') {
    return false
  }

  const candidate = error as Partial<ApiServiceError>
  return candidate.message === 'API Service Error'
    && candidate.code === 'INTERNAL_SERVER_ERROR'
    && typeof candidate.detail === 'string'
}

export const isApiServiceErrorResponse = (response: unknown): response is { code: 'INTERNAL_SERVER_ERROR'; message: 'API Service Error'; data: string } => {
  if (!response || typeof response !== 'object') {
    return false
  }

  const candidate = response as { code?: unknown; message?: unknown; data?: unknown }
  return candidate.code === 'INTERNAL_SERVER_ERROR'
    && candidate.message === 'API Service Error'
    && typeof candidate.data === 'string'
}
