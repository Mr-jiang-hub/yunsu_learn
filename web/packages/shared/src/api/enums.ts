/**
 * API 对外稳定枚举，见 docs/design/04.API接口设计.md 3.8。
 * 前端未知值必须降级显示原始 code，不得崩溃。
 */
export const StudentStatus = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
  CANCELLED: 'CANCELLED',
} as const

export const PracticeType = {
  STAGE: 'STAGE',
  WRONG_RETRY: 'WRONG_RETRY',
} as const

export const PracticeStatus = {
  IN_PROGRESS: 'IN_PROGRESS',
  FINISHED: 'FINISHED',
  VOIDED: 'VOIDED',
} as const

export const QuestionType = {
  TRUE_FALSE: 'TRUE_FALSE',
  SINGLE_CHOICE: 'SINGLE_CHOICE',
  MULTIPLE_CHOICE: 'MULTIPLE_CHOICE',
  SHORT_ANSWER: 'SHORT_ANSWER',
} as const

export const JudgeStatus = {
  PENDING: 'PENDING',
  JUDGED: 'JUDGED',
  PENDING_SELF_ASSESSMENT: 'PENDING_SELF_ASSESSMENT',
  SELF_ASSESSED: 'SELF_ASSESSED',
} as const

export const InterviewStatus = {
  CREATING: 'CREATING',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  INCOMPLETE: 'INCOMPLETE',
  EXPIRED: 'EXPIRED',
  VOIDED: 'VOIDED',
} as const

export const ReportStatus = {
  NORMAL: 'NORMAL',
  INCOMPLETE: 'INCOMPLETE',
} as const

export const ImportStatus = {
  PROCESSING: 'PROCESSING',
  SUCCEEDED: 'SUCCEEDED',
  FAILED: 'FAILED',
} as const

export const AnswerSource = {
  TEXT: 'TEXT',
  AUDIO: 'AUDIO',
} as const
