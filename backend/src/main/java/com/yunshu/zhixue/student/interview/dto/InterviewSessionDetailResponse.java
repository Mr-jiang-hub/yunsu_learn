package com.yunshu.zhixue.student.interview.dto;

import java.util.List;

/**
 * 会话详情与续答（04 §6.7.5）：返回全部消息、当前待答问题与可操作标记。
 */
public record InterviewSessionDetailResponse(
        InterviewSessionView session,
        List<InterviewMessageView> messages,
        InterviewMessageView currentQuestion,
        boolean canAnswer,
        boolean canFinish,
        boolean reportAvailable
) {
}
