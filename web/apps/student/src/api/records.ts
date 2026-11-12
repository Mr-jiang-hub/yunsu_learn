import { http } from './client'

/** C 端学习与面试记录（04 §6.8）。 */
export interface CourseRecordItem {
  courseId: number
  title: string
  subjectName: string
  stageName: string
  startedAt: string | null
  completedAt: string | null
}

export interface PracticeRecordItem {
  practiceId: number
  type: 'STAGE' | 'WRONG_RETRY'
  status: 'IN_PROGRESS' | 'FINISHED' | 'VOIDED'
  stageName: string
  questionCount: number
  correctCount: number
  correctRate: number
  masteryRate: number | null
  masteryEligible: boolean
  startedAt: string | null
  finishedAt: string | null
}

export interface MasteryTrendResponse {
  stageId: number
  stageName: string
  points: Array<{
    practiceId: number
    finishedAt: string | null
    correctRate: number
    masteryRate: number
  }>
}

export interface InterviewCompareResponse {
  base: { sessionId: number; subjectName: string; finishedAt: string | null; totalScore: number | null }
  target: { sessionId: number; subjectName: string; finishedAt: string | null; totalScore: number | null }
  dimensions: Array<{
    dimensionId: number
    dimensionName: string
    baseScore: number | null
    targetScore: number | null
    delta: number | null
  }>
  totalDelta: number | null
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

export const PRACTICE_TYPE_LABELS: Record<PracticeRecordItem['type'], string> = {
  STAGE: '阶段练习',
  WRONG_RETRY: '错题重练',
}

export const PRACTICE_STATUS_LABELS: Record<PracticeRecordItem['status'], string> = {
  IN_PROGRESS: '进行中',
  FINISHED: '已完成',
  VOIDED: '已作废',
}

export const recordsApi = {
  courses: (params: { page?: number; size?: number; subjectId?: number; stageId?: number } = {}) =>
    http.get<PageResult<CourseRecordItem>>('/c/records/courses', { params }),

  practices: (
    params: {
      page?: number
      size?: number
      type?: PracticeRecordItem['type']
      status?: PracticeRecordItem['status']
      subjectId?: number
    } = {},
  ) => http.get<PageResult<PracticeRecordItem>>('/c/records/practices', { params }),

  masteryTrend: (stageId: number, limit?: number) =>
    http.get<MasteryTrendResponse>('/c/records/mastery-trend', { params: { stageId, limit } }),

  compare: (baseSessionId: number, targetSessionId: number) =>
    http.get<InterviewCompareResponse>('/c/interview-sessions/compare', {
      params: { baseSessionId, targetSessionId },
    }),
}
