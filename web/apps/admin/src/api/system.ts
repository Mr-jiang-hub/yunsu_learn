import { http } from '@/api/client'

/** 单个业务前缀的巡检统计 */
export interface OrphanPrefixSummary {
  prefix: string
  objectCount: number
  orphanCount: number
  orphanBytes: number
}

/** 一条孤儿候选；后端只给 key，删除必须人工确认 */
export interface OrphanItem {
  key: string
  prefix: string
  size: number
  lastModified: string | null
}

/** 孤儿文件巡检结果（04-Q47 P0：只读巡检，不自动删除） */
export interface OrphanFileReport {
  prefixes: OrphanPrefixSummary[]
  items: OrphanItem[]
  truncated: boolean
  scannedObjects: number
  orphanCount: number
  orphanBytes: number
  scannedAt: string
}

export const PREFIX_LABELS: Record<string, string> = {
  subject: '学科封面',
  course: '课程封面',
  avatar: '学员头像',
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

export const systemApi = {
  orphanFiles: () => http.get<OrphanFileReport>('/admin/system/orphan-files'),
}
