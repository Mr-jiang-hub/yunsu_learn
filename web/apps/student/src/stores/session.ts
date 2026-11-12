import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { http, refreshAccessToken, tokenStore } from '@/api/client'

/** 登录/注册响应里的主体摘要（04 §6.1.2） */
interface StudentSummary {
  id: number
  nickname: string | null
  phoneMasked: string | null
  currentPathId: number | null
  hasPassword: boolean
}

/** GET /c/students/me 响应（04 §6 第 37 号接口） */
export interface StudentProfile {
  studentId: number
  nickname: string | null
  phoneMasked: string | null
  avatarUrl: string | null
  status: number
  currentPath: { pathId: number; pathName: string } | null
  hasPassword: boolean
  createdAt: string | null
}

interface AuthSession {
  accessToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
  student: StudentSummary
}

interface SendSmsCodeResult {
  sent: boolean
  expireSeconds: number
  retryAfterSeconds: number
  remainingToday: number
}

/** 验证码用途，与后端 SmsPurpose 枚举同名 */
export type SmsPurpose =
  | 'REGISTER'
  | 'LOGIN'
  | 'RESET_PASSWORD'
  | 'CHANGE_PHONE_OLD'
  | 'CHANGE_PHONE_NEW'
  | 'CANCEL'
  | 'CHANGE_PASSWORD'

export const useSessionStore = defineStore('session', () => {
  const authenticated = ref(Boolean(tokenStore.getAccessToken()))
  const profile = ref<StudentProfile | null>(null)

  const isAuthenticated = computed(() => authenticated.value)
  const nickname = computed(() => profile.value?.nickname ?? '学员')

  function syncFromTokenStore(): void {
    authenticated.value = Boolean(tokenStore.getAccessToken())
  }

  /** 只清本地状态，不请求后端 */
  function resetLocal(): void {
    tokenStore.clear()
    authenticated.value = false
    profile.value = null
  }

  async function sendSmsCode(phone: string, purpose: SmsPurpose): Promise<SendSmsCodeResult> {
    return http.post<SendSmsCodeResult>('/c/auth/sms-codes', { phone, purpose })
  }

  /** 登录/注册成功后统一落 token 并拉一次完整资料 */
  async function applySession(session: AuthSession): Promise<void> {
    tokenStore.setAccessToken(session.accessToken)
    authenticated.value = true
    profile.value = {
      studentId: session.student.id,
      nickname: session.student.nickname,
      phoneMasked: session.student.phoneMasked,
      avatarUrl: null,
      status: 1,
      currentPath: null,
      hasPassword: session.student.hasPassword,
      createdAt: null,
    }
    await loadProfile()
  }

  /** 补全资料；失败不阻断登录（例如接口临时不可用） */
  async function loadProfile(): Promise<void> {
    try {
      profile.value = await http.get<StudentProfile>('/c/students/me')
    } catch {
      // 保留登录/注册时拿到的摘要，避免整页空白
    }
  }

  async function loginByPassword(phone: string, password: string): Promise<void> {
    const session = await http.post<AuthSession>('/c/auth/login/password', { phone, password })
    await applySession(session)
  }

  async function loginBySms(phone: string, code: string): Promise<void> {
    const session = await http.post<AuthSession>('/c/auth/login/sms', { phone, code })
    await applySession(session)
  }

  async function register(phone: string, code: string, password: string, nickname: string): Promise<void> {
    const session = await http.post<AuthSession>('/c/auth/register', { phone, code, password, nickname })
    await applySession(session)
  }

  /**
   * 恢复会话：刷新页面后内存里的 access token 已丢失，
   * 先用 HttpOnly Cookie 换新的 access token，再拉资料。
   */
  async function restore(): Promise<void> {
    try {
      if (!tokenStore.getAccessToken()) {
        const refreshed = await refreshAccessToken()
        if (!refreshed) {
          resetLocal()
          return
        }
      }
      const data = await http.get<StudentProfile>('/c/students/me')
      profile.value = data
      authenticated.value = true
    } catch {
      resetLocal()
    }
  }

  /** 登出：先撤销服务端 refresh token 并清 Cookie，再清本地 */
  async function logout(): Promise<void> {
    try {
      await http.post('/c/auth/logout')
    } catch {
      // 登出接口幂等，失败也继续清本地
    }
    resetLocal()
  }

  return {
    isAuthenticated,
    nickname,
    profile,
    syncFromTokenStore,
    resetLocal,
    sendSmsCode,
    loadProfile,
    loginByPassword,
    loginBySms,
    register,
    restore,
    logout,
  }
})
