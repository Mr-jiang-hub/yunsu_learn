import { http } from './client'

/** GET /c/home（04 §6.2）：四个区块任一失败会置 null，并在 meta.degradedSections 里列出 */
export interface HomeCurrentPath {
  pathId: number
  pathName: string
  subjectName: string
  stageCount: number
  completedStageCount: number
  progressRate: number
  masteryRate: number
  hasPracticeSample: boolean
}

export interface HomeContinueLearning {
  stageId: number
  stageName: string
  courseId: number
  courseName: string
  progressStatus: 'IN_PROGRESS' | 'NOT_STARTED' | 'COMPLETED'
}

export interface HomePractice {
  stageId: number
  stageName: string
  canStart: boolean
  inProgressPracticeId: number | null
  lastFinishedAt: string | null
}

export interface HomeInterview {
  quotaDate: string
  limit: number
  used: number
  remaining: number
  canStart: boolean
  lastReportId: number | null
}

export type ActivityType = 'COURSE_COMPLETED' | 'PRACTICE_FINISHED' | 'INTERVIEW_FINISHED'

export const ACTIVITY_LABELS: Record<ActivityType, string> = {
  COURSE_COMPLETED: '课程',
  PRACTICE_FINISHED: '练习',
  INTERVIEW_FINISHED: '面试',
}

export interface HomeActivity {
  type: ActivityType
  title: string
  occurredAt: string
}

export interface HomeNotice {
  noticeId: number
  title: string
  content: string
  /** 置顶只影响排序，不额外占用首页 3 条配额 */
  pinned: boolean
  publishedAt: string
}

export interface HomeDegradedSection {
  section: string
  code: number
  message: string
}

export interface HomeResponse {
  hasPath: boolean
  currentPath: HomeCurrentPath | null
  continueLearning: HomeContinueLearning | null
  practice: HomePractice | null
  interview: HomeInterview | null
  recentActivities: HomeActivity[]
  notices: HomeNotice[]
  meta: {
    dataAsOf: string
    cacheTtlSeconds: number
    degradedSections: HomeDegradedSection[]
  }
}

export const homeApi = {
  get: () => http.get<HomeResponse>('/c/home'),
}
