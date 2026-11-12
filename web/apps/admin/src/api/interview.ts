import { http } from './client'

/** 04 §6.10.5 面试设置：难度档位（全局 3 档）与学科能力维度 */
export type ApiStatus = 'ENABLED' | 'DISABLED'

export interface DifficultyItem {
  id: number
  code: string
  name: string
  description: string
  focus: string
  sortNo: number
  status: ApiStatus
}

export interface DimensionItem {
  id: number
  subjectId: number
  name: string
  description: string
  sortNo: number
  status: ApiStatus
}

export interface UpdateDifficultyPayload {
  name?: string
  description?: string
  focus?: string
  sortNo?: number
}

export interface UpdateDimensionPayload {
  name?: string
  description?: string
  sortNo?: number
}

export interface CreateDimensionPayload extends UpdateDimensionPayload {
  subjectId: number
  name: string
}

export interface ReorderDimensionPayload {
  subjectId: number
  items: Array<{ id: number; sortNo: number }>
}

export const interviewApi = {
  difficulties: {
    list: () => http.get<{ items: DifficultyItem[] }>('/admin/interview-difficulties'),
    update: (id: number, payload: UpdateDifficultyPayload) =>
      http.patch<DifficultyItem>(`/admin/interview-difficulties/${id}`, payload),
    enable: (id: number) => http.post<{ id: number; status: ApiStatus }>(`/admin/interview-difficulties/${id}/enable`),
    disable: (id: number) => http.post<{ id: number; status: ApiStatus }>(`/admin/interview-difficulties/${id}/disable`),
  },
  dimensions: {
    list: (subjectId: number) =>
      http.get<{ items: DimensionItem[] }>('/admin/dimensions', { params: { subjectId } }),
    create: (payload: CreateDimensionPayload) => http.post<DimensionItem>('/admin/dimensions', payload),
    update: (id: number, payload: UpdateDimensionPayload) =>
      http.patch<DimensionItem>(`/admin/dimensions/${id}`, payload),
    enable: (id: number) => http.post<{ id: number; status: ApiStatus }>(`/admin/dimensions/${id}/enable`),
    disable: (id: number) => http.post<{ id: number; status: ApiStatus }>(`/admin/dimensions/${id}/disable`),
    reorder: (payload: ReorderDimensionPayload) => http.put<null>('/admin/dimensions/order', payload),
  },
}
