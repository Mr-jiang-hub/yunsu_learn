import { http } from './client'

/** 04 §6.5 阶段练习与错题重练 */
export type QuestionType = 'TRUE_FALSE' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'SHORT_ANSWER'
export type JudgeStatus = 'JUDGED' | 'PENDING_SELF_ASSESSMENT' | 'SELF_ASSESSED' | 'PENDING'
export type PracticeStatus = 'IN_PROGRESS' | 'FINISHED' | 'VOIDED'

export interface OptionView {
  optionId: number
  content: string
}

export interface QuestionView {
  questionId: number
  type: QuestionType
  content: string
  options: OptionView[]
}

export interface PracticeStartResponse {
  practiceId: number
  type: 'STAGE' | 'WRONG_RETRY'
  status: PracticeStatus
  questionCount: number
  answeredCount: number
  expireAt: string
  reused: boolean
  firstQuestion: QuestionView | null
}

export interface HitPoint {
  point: string
  hit: boolean
}

export interface JudgeView {
  questionId: number
  judgeStatus: JudgeStatus
  scored: boolean
  degraded: boolean
  isCorrect: boolean | null
  score: number | null
  correctOptionIds: number[]
  analysis: string
  referenceAnswer: string | null
  hitPoints: HitPoint[]
  answeredAt: string | null
}

export interface MyAnswer {
  selectedOptionIds: number[]
  answerText: string | null
}

export interface PracticeDetailResponse {
  practice: {
    practiceId: number
    type: 'STAGE' | 'WRONG_RETRY'
    status: PracticeStatus
    stageId: number | null
    stageName: string | null
    startedAt: string
    expireAt: string
    questionCount: number
    answeredCount: number
    remainingCount: number
    currentIndex: number
  }
  currentQuestion: QuestionView | null
  answered: Array<{ questionId: number; myAnswer: MyAnswer; judge: JudgeView }>
  canSubmit: boolean
}

export interface SubmitAnswerResponse {
  answer: JudgeView
  progress: {
    answeredCount: number
    remainingCount: number
    currentIndex: number
    practiceStatus: PracticeStatus
  }
  nextQuestion: QuestionView | null
  replayed: boolean
}

export interface PracticeSubmitResponse {
  practiceId: number
  status: PracticeStatus
  questionCount: number
  answeredCount: number
  correctCount: number
  correctRate: number
  durationSeconds: number
  masteryRate: number | null
  masteryEligible: boolean
  finishedAt: string | null
}

export interface PracticeResultResponse {
  practiceId: number
  type: 'STAGE' | 'WRONG_RETRY'
  status: PracticeStatus
  stageId: number | null
  stageName: string | null
  questionCount: number
  answeredCount: number
  correctCount: number
  correctRate: number
  masteryRate: number | null
  masteryEligible: boolean
  durationSeconds: number
  finishedAt: string | null
  wrongQuestions: Array<{
    questionId: number
    type: QuestionType
    content: string
    myAnswer: MyAnswer
    correctOptionIds: number[]
    referenceAnswer: string | null
    analysis: string
    score: number | null
    degraded: boolean
  }>
}

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  TRUE_FALSE: '判断题',
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  SHORT_ANSWER: '简答题',
}

export const practiceApi = {
  startStage: (stageId: number) => http.post<PracticeStartResponse>(`/c/stages/${stageId}/practices`),
  startWrongRetry: (body?: { subjectId?: number; stageId?: number }) =>
    http.post<PracticeStartResponse>('/c/practices/wrong-questions', body ?? {}),
  detail: (practiceId: number) => http.get<PracticeDetailResponse>(`/c/practices/${practiceId}`),
  answer: (practiceId: number, body: {
    questionId: number
    selectedOptionIds?: number[]
    answerText?: string
  }) => http.post<SubmitAnswerResponse>(`/c/practices/${practiceId}/answers`, body),
  selfAssess: (practiceId: number, questionId: number, isCorrect: boolean) =>
    http.post<JudgeView>(`/c/practices/${practiceId}/answers/${questionId}/self-assessment`, { isCorrect }),
  submit: (practiceId: number) => http.post<PracticeSubmitResponse>(`/c/practices/${practiceId}/submit`),
  result: (practiceId: number) => http.get<PracticeResultResponse>(`/c/practices/${practiceId}/result`),
}

/** 04 §6.6 错题本 */
export interface WrongQuestionItem {
  questionId: number
  type: QuestionType
  contentPreview: string
  subjectId: number
  subjectName: string
  stageId: number
  stageName: string
  lastWrongAt: string
  addedAt: string
}

export interface WrongQuestionPage {
  items: WrongQuestionItem[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

export interface WrongQuestionDetail {
  question: {
    questionId: number
    type: QuestionType
    content: string
    options: Array<{ optionId: number; optionKey: string; content: string; isCorrect: boolean }>
  }
  myLastAnswer: MyAnswer
  correctOptionIds: number[]
  referenceAnswer: string | null
  analysis: string
  scoringPoints: string[]
  lastWrongAt: string
  addedAt: string
}

export const wrongBookApi = {
  list: (params: { page?: number; size?: number; subjectId?: number; stageId?: number }) =>
    http.get<WrongQuestionPage>('/c/wrong-questions', { params }),
  detail: (questionId: number) => http.get<WrongQuestionDetail>(`/c/wrong-questions/${questionId}`),
}
