import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { http, refreshAccessToken, tokenStore } from '@/api/client'

interface Admin {
  adminId: number
  username: string
  displayName: string
  roleId: number
  roleCode: string
  roleName: string
}

interface Role {
  roleId: number
  roleCode: string
  roleName: string
}

/** B-01 登录响应；refreshToken 不进 body，已在 Set-Cookie 中 */
interface LoginResult {
  accessToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
  admin: Admin
  modules: string[]
  mustChangePassword: boolean
}

/** GET /admin/auth/me 响应 */
interface MeResult {
  admin: Admin
  role: Role
  modules: string[]
}

export const useSessionStore = defineStore('session', () => {
  const authenticated = ref(Boolean(tokenStore.getAccessToken()))
  const admin = ref<Admin | null>(null)
  const role = ref<Role | null>(null)
  const modules = ref<string[]>([])
  const mustChangePassword = ref(false)

  const isAuthenticated = computed(() => authenticated.value)

  function syncFromTokenStore(): void {
    authenticated.value = Boolean(tokenStore.getAccessToken())
  }

  /** 只清本地状态，不请求后端 */
  function resetLocal(): void {
    tokenStore.clear()
    authenticated.value = false
    admin.value = null
    role.value = null
    modules.value = []
    mustChangePassword.value = false
  }

  /** 登录：失败时抛 ApiError，由调用方展示后端返回的错误消息 */
  async function login(username: string, password: string): Promise<void> {
    const data = await http.post<LoginResult>('/admin/auth/login', { username, password })
    tokenStore.setAccessToken(data.accessToken)
    authenticated.value = true
    admin.value = data.admin
    role.value = {
      roleId: data.admin.roleId,
      roleCode: data.admin.roleCode,
      roleName: data.admin.roleName,
    }
    modules.value = data.modules
    mustChangePassword.value = data.mustChangePassword
  }

  /**
   * 恢复会话：刷新页面后内存里的 access token 已丢失，
   * 先用 HttpOnly Cookie 换新的 access token，再拉 /me 恢复菜单与权限。
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
      const data = await http.get<MeResult>('/admin/auth/me')
      admin.value = data.admin
      role.value = data.role
      modules.value = data.modules
      authenticated.value = true
    } catch {
      resetLocal()
    }
  }

  /** 登出：先撤销服务端 refresh token 并清 Cookie，再清本地 */
  async function logout(): Promise<void> {
    try {
      await http.post('/admin/auth/logout')
    } catch {
      // 登出接口幂等，失败也继续清本地
    }
    resetLocal()
  }

  return {
    isAuthenticated,
    syncFromTokenStore,
    resetLocal,
    login,
    restore,
    logout,
    admin,
    role,
    modules,
    mustChangePassword,
  }
})
