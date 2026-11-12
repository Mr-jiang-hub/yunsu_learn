package com.yunshu.zhixue.student.interview.dto;

/**
 * 创建面试会话响应（04 §6.7.3）。`reused=true` 表示 60 秒内同配置会话复用，未扣次数。
 */
public record CreateSessionResponse(
        InterviewSessionView session,
        InterviewMessageView openingQuestion,
        InterviewQuotaView quota,
        boolean reused
) {
}
