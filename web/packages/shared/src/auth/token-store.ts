import type { TokenPair } from '../api/types'

/**
 * 登录态存储抽象。
 *
 * 注意：02 架构文档 §2.6 描述 Refresh Token 放 HttpOnly Cookie，
 * 而 04 API 文档 §4.1/§4.2 的登录与刷新响应在 JSON body 中返回 refreshToken，
 * 两者存在冲突。当前基础工程按 04 的接口契约实现：access 仅在内存，
 * refresh 暂存 sessionStorage；冲突确认后再决定是否切换 Cookie 方案。
 */
export interface TokenStore {
  getAccessToken(): string | null
  getRefreshToken(): string | null
  /** 仅设置内存中的 access token（refresh 走 HttpOnly Cookie 时使用） */
  setAccessToken(token: string): void
  setTokens(tokens: TokenPair): void
  clear(): void
}

const REFRESH_TOKEN_KEY = 'yunshu.refresh-token'

export interface TokenStoreOptions {
  /** 传入 null 表示不持久化 refresh token（仅内存） */
  refreshStorage?: Storage | null
}

export function createTokenStore(options: TokenStoreOptions = {}): TokenStore {
  let accessToken: string | null = null
  const storage =
    options.refreshStorage === undefined
      ? typeof window === 'undefined'
        ? null
        : window.sessionStorage
      : options.refreshStorage

  return {
    getAccessToken: () => accessToken,
    getRefreshToken: () => storage?.getItem(REFRESH_TOKEN_KEY) ?? null,
    setAccessToken: (token: string) => {
      accessToken = token
    },
    setTokens: (tokens: TokenPair) => {
      accessToken = tokens.accessToken
      storage?.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
    },
    clear: () => {
      accessToken = null
      storage?.removeItem(REFRESH_TOKEN_KEY)
    },
  }
}
