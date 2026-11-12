import { http } from '@/api/client'

export interface OperationLogItem {
  id: number
  operatorId: number
  operatorName: string
  roleName: string
  moduleCode: string
  action: string
  targetType: string
  targetId: number | null
  result: 'SUCCESS' | 'FAILED' | 'DENIED' | 'UNKNOWN'
  detail: string
  ip: string
  traceId: string
  createdAt: string | null
}

export interface OperationLogPage {
  items: OperationLogItem[]
  nextCursor: string | null
  hasMore: boolean
}

export interface OperationLogParams {
  cursor?: string
  limit?: number
  moduleCode?: string
  action?: string
  targetType?: string
  result?: string
  startAt?: string
  endAt?: string
}

/** 模块白名单，与后端 admin_role_module.module_code 一致 */
export const MODULE_LABELS: Record<string, string> = {
  subject: '学科',
  course: '课程',
  question: '题库',
  interview_settings: '面试设置',
  learning_path: '学习路线',
  student: '学员',
  admin_user: '后台账号',
  role: '角色',
  operation_log: '操作日志',
  dashboard: '数据看板',
}

export const ACTION_LABELS: Record<string, string> = {
  create: '新增',
  update: '编辑',
  enable: '启用',
  disable: '停用',
  'reset-password': '重置密码',
  cancel: '注销',
  login: '登录',
}

export const RESULT_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  DENIED: '越权拒绝',
}

function clean(params: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  })
  return result
}

export const logApi = {
  list: (params: OperationLogParams) =>
    http.get<OperationLogPage>('/admin/operation-logs', {
      params: clean(params as Record<string, unknown>),
    }),
}
