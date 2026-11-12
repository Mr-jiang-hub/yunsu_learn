import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import type { TokenStore } from '../auth/token-store'
import { ErrorCodes } from './error-codes'
import { ApiError } from './errors'
import type { ApiResponse } from './types'

export interface HttpClientOptions {
  baseURL: string
  timeout?: number
  tokenStore?: TokenStore
  /** refresh token 走 HttpOnly Cookie 时置 true，跨域/同源均携带凭证 */
  withCredentials?: boolean
  onUnauthorized?: (error: ApiError) => void
  /** 刷新 access token；返回 true 表示刷新成功，原请求会自动重放一次 */
  refresh?: () => Promise<boolean>
}

export interface HttpClient {
  raw: AxiosInstance
  request<T>(config: AxiosRequestConfig): Promise<T>
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

type RetryableConfig = InternalAxiosRequestConfig & { __retried?: boolean }

/**
 * 统一 HTTP 客户端：注入 access token 与 X-Trace-Id，解包 ApiResponse，
 * 业务失败统一抛 ApiError；401 且 Access Token 过期时尝试刷新并重放一次。
 */
export function createHttpClient(options: HttpClientOptions): HttpClient {
  const instance = axios.create({
    baseURL: options.baseURL,
    timeout: options.timeout ?? 15_000,
    withCredentials: options.withCredentials ?? false,
  })

  let refreshing: Promise<boolean> | null = null
  const runRefresh = (): Promise<boolean> => {
    if (!options.refresh) {
      return Promise.resolve(false)
    }
    if (!refreshing) {
      refreshing = options
        .refresh()
        .catch(() => false)
        .finally(() => {
          refreshing = null
        })
    }
    return refreshing
  }

  instance.interceptors.request.use((config) => {
    const accessToken = options.tokenStore?.getAccessToken()
    if (accessToken) {
      config.headers.set('Authorization', `Bearer ${accessToken}`)
    }
    if (!config.headers.has('X-Trace-Id')) {
      config.headers.set('X-Trace-Id', newTraceId())
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const body = response.data as ApiResponse<unknown> | undefined
      if (body && typeof body.code === 'number') {
        if (body.code === ErrorCodes.OK) {
          return body.data as never
        }
        throw new ApiError(body.code, body.message, body.data, body.traceId, response.status)
      }
      return response.data as never
    },
    async (error: AxiosError) => {
      const response = error.response
      const body = response?.data as ApiResponse<unknown> | undefined
      if (response && body && typeof body.code === 'number') {
        const apiError = new ApiError(body.code, body.message, body.data, body.traceId, response.status)
        const config = error.config as RetryableConfig | undefined

        if (
          response.status === 401 &&
          apiError.code === ErrorCodes.ACCESS_TOKEN_EXPIRED &&
          config &&
          !config.__retried
        ) {
          const refreshed = await runRefresh()
          if (refreshed) {
            config.__retried = true
            return instance.request(config)
          }
        }

        if (response.status === 401) {
          options.onUnauthorized?.(apiError)
        }
        return Promise.reject(apiError)
      }

      return Promise.reject(
        new ApiError(-1, error.message || '网络请求失败', null, '', response?.status ?? 0),
      )
    },
  )

  // 响应拦截器已把 AxiosResponse 解包为业务数据，axios 的类型仍以为返回 AxiosResponse，
  // 且新版 request 的泛型返回是条件类型，无法直接推导，这里显式收窄。
  const request = <T>(config: AxiosRequestConfig): Promise<T> =>
    instance.request(config) as unknown as Promise<T>

  return {
    raw: instance,
    request,
    get: (url, config) => request({ ...config, url, method: 'GET' }),
    post: (url, data, config) => request({ ...config, url, data, method: 'POST' }),
    put: (url, data, config) => request({ ...config, url, data, method: 'PUT' }),
    patch: (url, data, config) => request({ ...config, url, data, method: 'PATCH' }),
    delete: (url, config) => request({ ...config, url, method: 'DELETE' }),
  }
}

/** 生成 16 位十六进制追踪 ID，由服务端沿用并回传 */
export function newTraceId(): string {
  const bytes = new Uint8Array(8)
  if (typeof crypto !== 'undefined' && 'getRandomValues' in crypto) {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < bytes.length; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256)
    }
  }
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/** 创建类 POST 使用幂等键，服务端按主体 + 路径 + key 去重 */
export function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
