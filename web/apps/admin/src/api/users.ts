import { http } from '@/api/client'

export type AdminStatus = 'ACTIVE' | 'DISABLED'

export interface AdminUserItem {
  id: number
  username: string
  displayName: string
  roleId: number
  roleName: string
  status: AdminStatus
  lastLoginAt: string | null
  createdAt: string | null
}

export interface AdminUserDetail extends AdminUserItem {
  roleCode: string
  mustChangePassword: boolean
  updatedAt: string | null
}

export interface AdminUserPayload {
  username?: string
  displayName?: string
  password?: string
  roleId?: number
}

export interface RoleItem {
  id: number
  code: string
  name: string
  description: string
  moduleCount: number
  builtIn: boolean
}

export interface RoleDetail {
  id: number
  code: string
  name: string
  description: string
  modules: string[]
}

export interface ResetPasswordResult {
  temporaryPassword: string
  mustChangePassword: boolean
  resetAt: string
}

/** 模块白名单，与后端 admin_role_module.module_code 一致 */
export const MODULE_OPTIONS: { code: string; label: string }[] = [
  { code: 'subject', label: '学科' },
  { code: 'course', label: '课程' },
  { code: 'question', label: '题库' },
  { code: 'interview_settings', label: '面试设置' },
  { code: 'learning_path', label: '学习路线' },
  { code: 'student', label: '学员管理' },
  { code: 'admin_user', label: '后台账号' },
  { code: 'role', label: '角色权限' },
  { code: 'operation_log', label: '操作日志' },
  { code: 'dashboard', label: '数据看板' },
]

function clean(params: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  })
  return result
}

export const adminUserApi = {
  list: (params: { page?: number; size?: number; roleId?: number; status?: string; keyword?: string }) =>
    http.get<{ items: AdminUserItem[]; page: number; size: number; total: number }>('/admin/admin-users', {
      params: clean(params),
    }),
  detail: (id: number) => http.get<AdminUserDetail>(`/admin/admin-users/${id}`),
  create: (payload: AdminUserPayload) => http.post<AdminUserDetail>('/admin/admin-users', payload),
  update: (id: number, payload: AdminUserPayload) => http.patch<AdminUserDetail>(`/admin/admin-users/${id}`, payload),
  enable: (id: number) => http.post<{ id: number; status: string }>(`/admin/admin-users/${id}/enable`),
  disable: (id: number) => http.post<{ id: number; status: string }>(`/admin/admin-users/${id}/disable`),
  resetPassword: (id: number) => http.post<ResetPasswordResult>(`/admin/admin-users/${id}/reset-password`),
}

export const roleApi = {
  list: () => http.get<{ items: RoleItem[] }>('/admin/roles'),
  detail: (id: number) => http.get<RoleDetail>(`/admin/roles/${id}`),
  updateModules: (id: number, moduleCodes: string[]) =>
    http.put<RoleDetail>(`/admin/roles/${id}/modules`, { moduleCodes }),
}
