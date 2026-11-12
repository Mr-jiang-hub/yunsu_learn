import { http } from './client'
import type { StudentProfile } from '@/stores/session'

/** 图片上传结果：库里存 key，url 是 30 分钟签名地址 */
export interface UploadResult {
  key: string
  url: string
  expireAt: string
}

export interface PhoneChangeResult {
  phoneMasked: string
  changedAt: string
}

export interface UpdateProfilePayload {
  nickname?: string
  avatarKey?: string
}

export interface SmsCodeResult {
  sent: boolean
  expireSeconds: number
  retryAfterSeconds: number
  remainingToday: number
}

/** 只能发给本人已绑定手机号：换绑验原号、注销验本人号 */
/** 本人发码用途；CHANGE_PASSWORD 为评审缺口 G-02 新增（PRD 5.10 允许验证码改密） */
export type SelfSmsPurpose = 'CHANGE_PHONE_OLD' | 'CANCEL' | 'CHANGE_PASSWORD'

/**
 * C 端个人中心（04 §6.9）。
 * 改密 / 换绑 / 注销成功后服务端会撤销全部 refresh token，前端需重新登录。
 */
export const profileApi = {
  me: () => http.get<StudentProfile>('/c/students/me'),

  update: (payload: UpdateProfilePayload) => http.patch<StudentProfile>('/c/students/me', payload),

  /**
   * 改密：原密码与验证码**二选一**（PRD 5.10 / 缺口 G-02）。
   * 两个都为 null 或都传，后端返回 10001——不做静默偏向。
   */
  changePassword: (payload: {
    oldPassword?: string | null
    newPassword: string
    smsCode?: string | null
  }) => http.put<null>('/c/students/me/password', payload),

  changePhone: (oldCode: string, newPhone: string, newCode: string) =>
    http.post<PhoneChangeResult>('/c/students/me/phone-change', { oldCode, newPhone, newCode }),

  cancel: (code: string, confirmText: string) =>
    http.post<null>('/c/students/me/cancel', { code, confirmText }),

  /** 发给本人已绑定手机号的验证码（前端只有掩码号，由服务端按 token 取号） */
  sendSelfCode: (purpose: SelfSmsPurpose) =>
    http.post<SmsCodeResult>('/c/students/me/sms-codes', { purpose }),

  /** 头像上传：purpose 目前只支持 AVATAR，文件校验（格式/2MB/4096px）在服务端做 */
  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.post<UploadResult>('/c/uploads/images?purpose=AVATAR', form)
  },
}
