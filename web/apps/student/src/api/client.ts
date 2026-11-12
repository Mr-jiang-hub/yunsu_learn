import axios from 'axios'
import {
  ErrorCodes,
  createHttpClient,
  createTokenStore,
  type ApiResponse,
} from '@yunshu/shared'

/**
 * C 端登录态：access token 仅存内存，refresh token 由后端以 HttpOnly Cookie
 * （ys_c_rt，Path=/api/v1/c）下发，前端不可读、不落任何本地存储。
 */
export const tokenStore = createTokenStore({ refreshStorage: null })

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

interface RefreshedSession {
  accessToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
}

/** 用 Cookie 换新的 access token；页面刷新后恢复登录态也走这里 */
export async function refreshAccessToken(): Promise<boolean> {
  try {
    const response = await axios.post<ApiResponse<RefreshedSession>>(
      `${baseURL}/c/auth/token/refresh`,
      null,
      {
        timeout: 15_000,
        withCredentials: true,
      },
    )
    const body = response.data
    if (body.code !== ErrorCodes.OK || !body.data) {
      return false
    }
    tokenStore.setAccessToken(body.data.accessToken)
    return true
  } catch {
    return false
  }
}

export const http = createHttpClient({
  baseURL,
  tokenStore,
  refresh: refreshAccessToken,
  withCredentials: true,
  onUnauthorized: () => {
    tokenStore.clear()
    if (import.meta.env.VITE_AUTH_GUARD_ENABLED === 'true') {
      const loginPath = import.meta.env.VITE_LOGIN_PATH || '/login'
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.assign(`${loginPath}?redirect=${redirect}`)
    }
  },
})
