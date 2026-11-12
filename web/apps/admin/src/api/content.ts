import { http } from '@/api/client'

/** 后端偏移分页响应（04 §2.3） */
export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

export type ApiStatus = 'ENABLED' | 'DISABLED'
export type QuestionType = 'TRUE_FALSE' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'SHORT_ANSWER'

export interface SubjectItem {
  id: number
  name: string
  description: string
  coverUrl: string
  sortNo: number
  status: ApiStatus
  pathId: number | null
  pathName: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface SubjectPayload {
  name?: string
  description?: string
  coverKey?: string
  sortNo?: number
}

export interface CourseItem {
  id: number
  title: string
  subjectId: number
  subjectName: string
  stageId: number
  stageName: string
  bilibiliUrl: string
  coverUrl: string
  durationSeconds: number | null
  sortNo: number
  status: ApiStatus
  description: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface CoursePayload {
  title?: string
  subjectId?: number
  stageId?: number
  bilibiliUrl?: string
  coverKey?: string
  description?: string
  durationSeconds?: number
  sortNo?: number
}

export interface OptionItem {
  optionId: number
  optionKey: string
  content: string
  isCorrect: boolean
  sortNo: number
}

export interface ScoringPointItem {
  scoringPointId: number
  pointText: string
  sortNo: number
}

export interface QuestionListItem {
  id: number
  type: QuestionType
  contentPreview: string
  subjectId: number
  subjectName: string
  stageId: number
  stageName: string
  status: ApiStatus
  createdAt: string | null
  updatedAt: string | null
}

export interface QuestionDetail {
  id: number
  type: QuestionType
  subjectId: number
  subjectName: string
  stageId: number
  stageName: string
  content: string
  options: OptionItem[]
  scoringPoints: ScoringPointItem[]
  analysis: string
  referenceAnswer: string | null
  status: ApiStatus
  createdAt: string | null
  updatedAt: string | null
}

export interface OptionInput {
  optionKey?: string
  content: string
  isCorrect: boolean
  sortNo?: number
}

export interface QuestionPayload {
  type?: QuestionType
  subjectId?: number
  stageId?: number
  content?: string
  options?: OptionInput[]
  scoringPoints?: { pointText: string; sortNo?: number }[]
  analysis?: string
  referenceAnswer?: string
  sortNo?: number
}

export interface StatusResult {
  id: number
  status: ApiStatus
}

export interface StageBrief {
  id: number
  name: string
  description: string
  sortNo: number
  status: ApiStatus
  courseCount: number
  questionCount: number
}

export interface LearningPathItem {
  id: number
  name: string
  subjectId: number
  subjectName: string
  description: string
  status: ApiStatus
  stageCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface LearningPathDetail {
  id: number
  name: string
  subjectId: number
  subjectName: string
  description: string
  status: ApiStatus
  stages: StageBrief[]
  createdAt: string | null
  updatedAt: string | null
}

export interface LearningPathPayload {
  subjectId?: number
  name?: string
  description?: string
  sortNo?: number
}

export interface StagePayload {
  name?: string
  description?: string
  sortNo?: number
}

export interface StageResponse {
  stageId: number
  pathId: number
  name: string
  description: string
  sortNo: number
  status: ApiStatus
  courseCount: number
  questionCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface StageContents {
  courses: {
    id: number
    title: string
    bilibiliUrl: string
    sortNo: number
    status: ApiStatus
  }[]
  questions: {
    id: number
    type: QuestionType
    contentPreview: string
    status: ApiStatus
  }[]
}

interface ListParams {
  page?: number
  size?: number
  keyword?: string
  /** 各模块的 status 取值不同（内容域 ENABLED/DISABLED，公告 DRAFT/PUBLISHED/OFFLINE） */
  status?: string
  subjectId?: number
  stageId?: number
  type?: QuestionType | ''
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

export const contentApi = {
  learningPaths: {
    list: (params: ListParams) =>
      http.get<PageResult<LearningPathItem>>('/admin/learning-paths', {
        params: clean(params as Record<string, unknown>),
      }),
    detail: (id: number) => http.get<LearningPathDetail>(`/admin/learning-paths/${id}`),
    create: (payload: LearningPathPayload) => http.post<LearningPathDetail>('/admin/learning-paths', payload),
    update: (id: number, payload: LearningPathPayload) =>
      http.patch<LearningPathDetail>(`/admin/learning-paths/${id}`, payload),
    enable: (id: number) => http.post<StatusResult>(`/admin/learning-paths/${id}/enable`),
    disable: (id: number) => http.post<StatusResult>(`/admin/learning-paths/${id}/disable`),
    createStage: (id: number, payload: StagePayload) =>
      http.post<StageResponse>(`/admin/learning-paths/${id}/stages`, payload),
    reorderStages: (id: number, items: { id: number; sortNo: number }[]) =>
      http.put<void>(`/admin/learning-paths/${id}/stages/order`, { items }),
  },
  stages: {
    update: (stageId: number, payload: StagePayload) =>
      http.patch<StageResponse>(`/admin/stages/${stageId}`, payload),
    enable: (stageId: number) => http.post<StatusResult>(`/admin/stages/${stageId}/enable`),
    disable: (stageId: number) => http.post<StatusResult>(`/admin/stages/${stageId}/disable`),
    contents: (stageId: number) => http.get<StageContents>(`/admin/stages/${stageId}/contents`),
  },
  subjects: {
    list: (params: ListParams) =>
      http.get<PageResult<SubjectItem>>('/admin/subjects', { params: clean(params as Record<string, unknown>) }),
    create: (payload: SubjectPayload) => http.post<SubjectItem>('/admin/subjects', payload),
    update: (id: number, payload: SubjectPayload) => http.patch<SubjectItem>(`/admin/subjects/${id}`, payload),
    enable: (id: number) => http.post<StatusResult>(`/admin/subjects/${id}/enable`),
    disable: (id: number) => http.post<StatusResult>(`/admin/subjects/${id}/disable`),
  },
  courses: {
    list: (params: ListParams) =>
      http.get<PageResult<CourseItem>>('/admin/courses', { params: clean(params as Record<string, unknown>) }),
    create: (payload: CoursePayload) => http.post<CourseItem>('/admin/courses', payload),
    update: (id: number, payload: CoursePayload) => http.patch<CourseItem>(`/admin/courses/${id}`, payload),
    enable: (id: number) => http.post<StatusResult>(`/admin/courses/${id}/enable`),
    disable: (id: number) => http.post<StatusResult>(`/admin/courses/${id}/disable`),
  },
  notices: {
    list: (params: ListParams) =>
      http.get<PageResult<NoticeItem>>('/admin/notices', { params: clean(params as Record<string, unknown>) }),
    create: (payload: NoticePayload) => http.post<NoticeItem>('/admin/notices', payload),
    update: (id: number, payload: NoticePayload) => http.patch<NoticeItem>(`/admin/notices/${id}`, payload),
    publish: (id: number) => http.post<NoticeItem>(`/admin/notices/${id}/publish`),
    offline: (id: number) => http.post<NoticeItem>(`/admin/notices/${id}/offline`),
    remove: (id: number) => http.delete<void>(`/admin/notices/${id}`),
  },
  messages: {
    list: (params: ListParams) =>
      http.get<PageResult<MessageItem>>('/admin/messages', { params: clean(params as Record<string, unknown>) }),
    create: (payload: MessagePayload) => http.post<MessageItem>('/admin/messages', payload),
    update: (id: number, payload: MessagePayload) => http.patch<MessageItem>(`/admin/messages/${id}`, payload),
    /** 发送：定向消息需要带 studentIds，全体消息可省略 body */
    send: (id: number, studentIds?: number[]) =>
      http.post<MessageItem>(`/admin/messages/${id}/send`, studentIds ? { studentIds } : undefined),
    revoke: (id: number) => http.post<MessageItem>(`/admin/messages/${id}/revoke`),
    remove: (id: number) => http.delete<void>(`/admin/messages/${id}`),
  },
  questions: {
    list: (params: ListParams) =>
      http.get<PageResult<QuestionListItem>>('/admin/questions', { params: clean(params as Record<string, unknown>) }),
    detail: (id: number) => http.get<QuestionDetail>(`/admin/questions/${id}`),
    create: (payload: QuestionPayload) => http.post<QuestionDetail>('/admin/questions', payload),
    update: (id: number, payload: QuestionPayload) => http.patch<QuestionDetail>(`/admin/questions/${id}`, payload),
    enable: (id: number) => http.post<StatusResult>(`/admin/questions/${id}/enable`),
    disable: (id: number) => http.post<StatusResult>(`/admin/questions/${id}/disable`),
    /** 导入模板 xlsx 文件流（走后端鉴权，不能用裸链接） */
    importTemplate: () => http.get<Blob>('/admin/questions/import-template', { responseType: 'blob' }),
    /** 上传并启动异步导入任务，返回 taskId */
    importQuestions: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return http.post<ImportStartResult>('/admin/questions/import', form)
    },
    /** 轮询结果；PROCESSING 时后端返回 40022 */
    importStatus: (taskId: number) =>
      http.get<ImportTaskResult>(`/admin/questions/import/${taskId}`),
  },
}

/** 公告状态：草稿 / 已发布 / 已下线 */
export type NoticeStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface NoticeItem {
  id: number
  title: string
  content: string
  status: NoticeStatus
  pinned: boolean
  sortNo: number
  publishedAt: string | null
  expireAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface NoticePayload {
  title?: string
  content?: string
  pinned?: boolean
  sortNo?: number
  /** RFC 3339；null 或省略表示长期有效，配合 clearExpire 清空 */
  expireAt?: string | null
  clearExpire?: boolean
}

export const NOTICE_STATUS_LABELS: Record<NoticeStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
}

/** 消息状态：草稿 / 已发送 / 已撤回 */
export type MessageStatus = 'DRAFT' | 'SENT' | 'REVOKED'

/** 消息受众：全体学员 / 指定学员 */
export type MessageAudience = 'ALL' | 'SPECIFIED'

/** 消息类型：系统通知 / 学习提醒 / 活动通知 */
export type MessageType = 'SYSTEM' | 'STUDY' | 'ACTIVITY'

export interface MessageItem {
  id: number
  title: string
  content: string
  type: MessageType
  audience: MessageAudience
  status: MessageStatus
  targetCount: number
  readCount: number
  sentAt: string | null
  createdAt: string
  updatedAt: string
}

export interface MessagePayload {
  title: string
  content: string
  type: MessageType
  audience: MessageAudience
  studentIds?: number[]
}

export const MESSAGE_STATUS_LABELS: Record<MessageStatus, string> = {
  DRAFT: '草稿',
  SENT: '已发送',
  REVOKED: '已撤回',
}

export const MESSAGE_TYPE_LABELS: Record<MessageType, string> = {
  SYSTEM: '系统通知',
  STUDY: '学习提醒',
  ACTIVITY: '活动通知',
}

export const MESSAGE_AUDIENCE_LABELS: Record<MessageAudience, string> = {
  ALL: '全体学员',
  SPECIFIED: '指定学员',
}

export interface ImportStartResult {
  taskId: number
  status: 'PROCESSING'
  createdAt: string
}

export interface ImportRowError {
  rowNo: number
  column: string
  message: string
}

export interface ImportTaskResult {
  taskId: number
  status: 'SUCCEEDED' | 'FAILED'
  totalRows: number
  successRows: number
  failedRows: number
  errors: ImportRowError[]
  createdAt: string
  finishedAt: string | null
}

/** 题目类型中文名，前后端映射用字符串枚举 */
export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  TRUE_FALSE: '判断题',
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  SHORT_ANSWER: '简答题',
}
