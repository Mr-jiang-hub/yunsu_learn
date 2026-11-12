package com.yunshu.zhixue.student.interview.dto;

/**
 * 面试消息视图（04 §6.7.5）。`role` 仅 `AI` / `STUDENT`；只返回最终文本，不返回音频。
 */
public record InterviewMessageView(
        long messageId,
        int round,
        String role,
        String content,
        String createdAt
) {
}
