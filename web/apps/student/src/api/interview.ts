import { idempotencyKey } from '@yunshu/shared'
import { http } from './client'

/**
 * C 端 AI 模拟面试（04 §6.7）。
 *
 * 状态语义：`IN_PROGRESS` 才能作答；`COMPLETED`/`INCOMPLETE` 才可能有报告；
 * `VOIDED` 表示开场题生成失败、次数已返还。
 */
export type InterviewStatus =
  | 'CREATING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'INCOMPLETE'
  | 'EXPIRED'
  | 'VOIDED'

export const INTERVIEW_STATUS_LABELS: Record<InterviewStatus, string> = {
  CREATING: '创建中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  INCOMPLETE: '未完成',
  EXPIRED: '已过期',
  VOIDED: '已作废',
}

export interface DifficultyOption {
  difficultyId: number
  code: string
  name: string
  description: string
  focus: string
  status: 'ENABLED'
}

export interface InterviewSubjectOption {
  subjectId: number
  subjectName: string
  pathId: number | null
  pathName: string | null
  stageId: number | null
  stageName: string | null
  dimensionCount: number
  difficulties: DifficultyOption[]
  canStart: boolean
  reasonCode: string | null
  reasonMessage: string | null
  remainingQuota: number
}

export interface InterviewOptionsResponse {
  items: InterviewSubjectOption[]
  quotaDate: string
  serverTime: string
}

export interface InterviewQuota {
  quotaDate: string
  limit: number
  used: number
  remaining: number
  resetsAt: string
  serverTime: string
}

export interface InterviewSessionView {
  sessionId: number
  subjectId: number
  subjectName: string
  difficultyId: number
  difficultyCode: string
  difficultyName: string
  stageId: number
  stageName: string
  status: InterviewStatus
  currentRound: number
  quotaDate: string
  startedAt: string | null
  expireAt: string | null
  finishedAt: string | null
  voidReason: string | null
}

export interface InterviewMessageView {
  messageId: number
  round: number
  role: 'AI' | 'STUDENT'
  content: string
  createdAt: string
}

export interface CreateSessionResponse {
  session: InterviewSessionView
  openingQuestion: InterviewMessageView | null
  quota: InterviewQuota
  reused: boolean
}

export interface InterviewSessionListItem {
  sessionId: number
  subjectId: number
  subjectName: string
  difficultyCode: string
  difficultyName: string
  stageName: string
  status: InterviewStatus
  currentRound: number
  startedAt: string | null
  finishedAt: string | null
  totalScore: number | null
  reportStatus: 'NORMAL' | 'INCOMPLETE' | null
  quotaDate: string
}

export interface InterviewSessionPage {
  items: InterviewSessionListItem[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

export interface InterviewSessionDetailResponse {
  session: InterviewSessionView
  messages: InterviewMessageView[]
  currentQuestion: InterviewMessageView | null
  canAnswer: boolean
  canFinish: boolean
  reportAvailable: boolean
}

export interface SubmitAnswerResponse {
  studentMessage: InterviewMessageView
  nextQuestion: InterviewMessageView | null
  completed: boolean
  sessionStatus: InterviewStatus
  degraded: boolean
  reportAvailable: boolean
  progress: { answeredRounds: number; currentRound: number }
  replayed: boolean
}

export interface AudioTranscribeResponse {
  round: number
  transcript: string
  asrDegraded: boolean
  fallbackToText: boolean
  durationSeconds: number | null
}

export interface FinishResponse {
  sessionId: number
  status: InterviewStatus
  finishedAt: string | null
  answeredRounds: number
  reportAvailable: boolean
}

export interface InterviewReportResponse {
  sessionId: number
  status: 'NORMAL' | 'INCOMPLETE'
  totalScore: number | null
  summary: string
  suggestions: string[]
  dimensions: Array<{ dimensionId: number; dimensionName: string; score: number }>
  generatedAt: string | null
}

export const interviewApi = {
  options: () => http.get<InterviewOptionsResponse>('/c/interview-options'),

  quota: () => http.get<InterviewQuota>('/c/interview-quota'),

  create: (subjectId: number, difficultyId: number) =>
    http.post<CreateSessionResponse>(
      '/c/interview-sessions',
      { subjectId, difficultyId },
      { headers: { 'Idempotency-Key': idempotencyKey() } },
    ),

  list: (params: { page?: number; size?: number; status?: InterviewStatus } = {}) =>
    http.get<InterviewSessionPage>('/c/interview-sessions', { params }),

  detail: (sessionId: number) =>
    http.get<InterviewSessionDetailResponse>(`/c/interview-sessions/${sessionId}`),

  answer: (sessionId: number, round: number, answerText: string, source: 'TEXT' | 'AUDIO' = 'TEXT') =>
    http.post<SubmitAnswerResponse>(`/c/interview-sessions/${sessionId}/answers`, {
      round,
      answerText,
      source,
    }),

  uploadAudio: (sessionId: number, round: number, blob: Blob) => {
    const form = new FormData()
    form.append('round', String(round))
    form.append('audio', blob, `answer-${round}.webm`)
    // axios 会自动带上 multipart 边界；响应拦截器已把 ApiResponse 解包成业务数据
    return http.post<AudioTranscribeResponse>(
      `/c/interview-sessions/${sessionId}/answers/audio`,
      form,
    )
  },

  finish: (sessionId: number, confirm: boolean) =>
    http.post<FinishResponse>(`/c/interview-sessions/${sessionId}/finish`, { confirm }),

  report: (sessionId: number) =>
    http.get<InterviewReportResponse>(`/c/interview-sessions/${sessionId}/report`),
}

/** 报告未生成时的短轮询：2 秒一次，最多 15 次（04 §6.7.8，不引入 SSE） */
export async function pollReport(sessionId: number, times = 15): Promise<InterviewReportResponse | null> {
  for (let i = 0; i < times; i += 1) {
    try {
      return await interviewApi.report(sessionId)
    } catch (caught) {
      const code = (caught as { code?: number }).code
      if (code !== 30051) {
        throw caught
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 2000))
  }
  return null
}
