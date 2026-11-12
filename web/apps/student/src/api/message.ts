import { http } from './client'

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

/** 消息类型：系统通知 / 学习提醒 / 活动通知 */
export type MessageType = 'SYSTEM' | 'STUDY' | 'ACTIVITY'

export const MESSAGE_TYPE_LABELS: Record<MessageType, string> = {
  SYSTEM: '系统通知',
  STUDY: '学习提醒',
  ACTIVITY: '活动通知',
}

export interface MessageItem {
  messageId: number
  title: string
  content: string
  type: MessageType
  sentAt: string
  /** null 表示未读 */
  readAt: string | null
}

export interface UnreadCountResult {
  unreadCount: number
}

export interface ReadAllResult {
  updated: number
}

/**
 * C 端消息中心。
 *
 * 全体消息是"懒投递"：学员首次调用列表或未读数时服务端才补收件行，
 * 因此注册之前发的消息不会出现在这里。已读/删除都是收件侧状态，
 * 后台撤回后消息直接消失，已读的消息 readAt 不会被覆盖。
 */
export const messageApi = {
  list: (params: { page?: number; size?: number } = {}) =>
    http.get<PageResult<MessageItem>>('/c/messages', { params }),

  unreadCount: () => http.get<UnreadCountResult>('/c/messages/unread-count'),

  read: (messageId: number) => http.post<MessageItem>(`/c/messages/${messageId}/read`),

  readAll: () => http.post<ReadAllResult>('/c/messages/read-all'),

  remove: (messageId: number) => http.delete<null>(`/c/messages/${messageId}`),
}
