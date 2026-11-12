import { http } from '@/api/client'

export type StudentStatus = 'ACTIVE' | 'DISABLED' | 'CANCELLED'

export interface StudentListItem {
  id: number
  nickname: string
  phoneMasked: string | null
  status: StudentStatus
  currentPathName: string | null
  lastLoginAt: string | null
  createdAt: string | null
}

export interface StudentPathRef {
  id: number
  name: string
}

export interface StudentDetail {
  student: {
    id: number
    nickname: string
    phoneMasked: string | null
    avatarUrl: string | null
    status: StudentStatus
    currentPath: StudentPathRef | null
    createdAt: string | null
    lastLoginAt: string | null
    cancelledAt: string | null
  }
  progress: {
    completedCourses: number
    courseCount: number
    masteryRate: number | null
    masteryQuestionCount: number | null
    masteryAt: string | null
    lastActiveAt: string | null
  }
  recentActivities: {
    type: 'COURSE' | 'PRACTICE' | 'INTERVIEW'
    title: string
    detail: string
    at: string | null
  }[]
  interviewSummary: {
    total: number
    completed: number
    lastFinishedAt: string | null
  }
}

export interface StudentListParams {
  page?: number
  size?: number
  phone?: string
  nickname?: string
  pathId?: number
  status?: StudentStatus | ''
  startDate?: string
  endDate?: string
}

export interface ResetPasswordResult {
  temporaryPassword: string
  mustChangePassword: boolean
  resetAt: string
}

export const STUDENT_STATUS_LABELS: Record<StudentStatus, string> = {
  ACTIVE: '正常',
  DISABLED: '已停用',
  CANCELLED: '已注销',
}

export const ACTIVITY_TYPE_LABELS: Record<string, string> = {
  COURSE: '课程',
  PRACTICE: '练习',
  INTERVIEW: '面试',
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

export const studentApi = {
  list: (params: StudentListParams) =>
    http.get<{ items: StudentListItem[]; page: number; size: number; total: number }>('/admin/students', {
      params: clean(params as Record<string, unknown>),
    }),
  detail: (id: number) => http.get<StudentDetail>(`/admin/students/${id}`),
  resetPassword: (id: number) => http.post<ResetPasswordResult>(`/admin/students/${id}/reset-password`),
  updateCurrentPath: (id: number, pathId: number) =>
    http.put<{ studentId: number; currentPathId: number; updatedAt: string }>(`/admin/students/${id}/current-path`, {
      pathId,
    }),
  enable: (id: number) => http.post<{ id: number; status: string }>(`/admin/students/${id}/enable`),
  disable: (id: number) => http.post<{ id: number; status: string }>(`/admin/students/${id}/disable`),
  cancel: (id: number, reason: string) =>
    http.post<{ id: number; status: string; cancelledAt: string }>(`/admin/students/${id}/cancel`, {
      confirmText: '注销',
      reason,
    }),
}
