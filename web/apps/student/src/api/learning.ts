import { http } from './client'

/** 04 §6.3 学习路线 */
export interface LearningPathCard {
  pathId: number
  pathName: string
  subjectId: number
  subjectName: string
  stageCount: number
  courseCount: number
  questionCount: number
  progressRate: number
  masteryRate: number
  isCurrent: boolean
}

export interface LearningPathListResponse {
  items: LearningPathCard[]
  currentPathId: number | null
}

export interface PathStageItem {
  stageId: number
  stageName: string
  sortNo: number
  courseCount: number
  completedCourseCount: number
  questionCount: number
  masteryRate: number
  hasPracticeSample: boolean
  status: 'ENABLED' | 'DISABLED'
}

export interface PathDetailResponse {
  path: {
    pathId: number
    pathName: string
    subjectId: number
    subjectName: string
    description: string
    stageCount: number
    courseCount: number
    questionCount: number
    progressRate: number
    masteryRate: number
    isCurrent: boolean
  }
  currentStageId: number | null
  stages: PathStageItem[]
}

export interface CurrentPathResponse {
  currentPathId: number
  pathName: string
  updatedAt: string
}

/** 04 §6.4 阶段与课程 */
export interface StageCourseItem {
  courseId: number
  title: string
  /** 评审缺口 G-01：B 站视频地址，空串表示未配置；由服务端下发，前端不得自行拼接 */
  bilibiliUrl: string
  coverUrl: string
  durationSeconds: number | null
  sortNo: number
  progressStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
  startedAt: string | null
  completedAt: string | null
  status: 'ENABLED' | 'DISABLED'
}

export interface StageDetailResponse {
  stage: {
    stageId: number
    stageName: string
    pathId: number
    pathName: string
    sortNo: number
    status: 'ENABLED' | 'DISABLED'
  }
  courses: StageCourseItem[]
  progress: {
    completedCourseCount: number
    courseCount: number
    progressRate: number
    masteryRate: number
    hasPracticeSample: boolean
  }
  practice: {
    canStart: boolean
    questionCount: number
    reason: string
  }
}

export interface CourseProgressResponse {
  courseId: number
  /** 评审缺口 G-01：开始/完成学习后可直接跳转的视频地址 */
  bilibiliUrl: string
  progressStatus: 'IN_PROGRESS' | 'COMPLETED'
  startedAt: string | null
  completedAt: string | null
}

export const learningApi = {
  paths: () => http.get<LearningPathListResponse>('/c/learning-paths'),
  pathDetail: (pathId: number) => http.get<PathDetailResponse>(`/c/learning-paths/${pathId}`),
  setCurrentPath: (pathId: number) =>
    http.put<CurrentPathResponse>('/c/students/me/current-path', { pathId }),
  stage: (stageId: number) => http.get<StageDetailResponse>(`/c/stages/${stageId}`),
  startCourse: (courseId: number) => http.post<CourseProgressResponse>(`/c/courses/${courseId}/start`),
  completeCourse: (courseId: number) =>
    http.post<CourseProgressResponse>(`/c/courses/${courseId}/complete`),
}

export function formatDuration(seconds: number | null): string {
  if (!seconds || seconds <= 0) {
    return ''
  }
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) {
    return `${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}
