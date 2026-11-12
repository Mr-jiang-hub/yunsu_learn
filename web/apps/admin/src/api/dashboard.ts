import { http } from '@/api/client'

export type DashboardRange = 'TODAY' | '7D' | '30D'

export interface DashboardData {
  range: DashboardRange
  startDate: string
  endDate: string
  sections: {
    overview: {
      studentTotal: number
      activeStudentCount: number
      newStudentCount: number
    } | null
    learning: {
      courseCompletionCount: number
      courseCompletionRate: number
      stageMasteryRate: number | null
    } | null
    practice: {
      practiceCount: number
      practiceCompletionRate: number
      averageCorrectRate: number | null
    } | null
    interview: {
      sessionCount: number
      completionRate: number
      averageScore: number | null
    } | null
    content: {
      subjectCount: number
      courseCount: number
      questionCount: number
    } | null
    users: {
      adminUserCount: number
      activeAdminCount: number
    } | null
  }
  meta: {
    dataAsOf: string | null
    cacheTtlSeconds: number
    degradedSections: string[]
  }
}

export const dashboardApi = {
  load: (range: DashboardRange) => http.get<DashboardData>('/admin/dashboard', { params: { range } }),
}
