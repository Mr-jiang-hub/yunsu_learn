/**
 * 统一响应与分页载体，契约见 docs/design/04.API接口设计.md 第 2 章。
 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface PageData<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

export interface CursorData<T> {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export interface FieldError {
  field: string
  code: 'REQUIRED' | 'PATTERN' | 'LENGTH' | 'RANGE' | 'ENUM' | 'FORMAT' | 'INVALID'
  message: string
}

export interface ValidationErrorData {
  errors: FieldError[]
  truncated?: boolean
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
}
