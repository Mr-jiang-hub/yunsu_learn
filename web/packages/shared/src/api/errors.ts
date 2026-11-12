/**
 * 业务/协议错误。code 来自服务端统一响应；网络错误使用 code=-1。
 */
export class ApiError extends Error {
  readonly code: number
  readonly data: unknown
  readonly traceId: string
  readonly httpStatus: number

  constructor(code: number, message: string, data: unknown, traceId: string, httpStatus: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
    this.traceId = traceId
    this.httpStatus = httpStatus
  }

  get isUnauthorized(): boolean {
    return this.httpStatus === 401
  }

  get isForbidden(): boolean {
    return this.httpStatus === 403
  }

  get isNetworkError(): boolean {
    return this.code === -1
  }
}
