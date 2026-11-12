package com.yunshu.zhixue.student.interview.dto;

/**
 * 提交一轮作答的响应（04 §6.7.6）。
 *
 * <p>`degraded=true` 表示追问生成失败、会话已按"未完成"结束（不回滚已保存回答）；
 * 同一轮重复提交相同内容时 `replayed=true`，不重新生成追问。</p>
 */
public record SubmitAnswerResponse(
        InterviewMessageView studentMessage,
        InterviewMessageView nextQuestion,
        boolean completed,
        String sessionStatus,
        boolean degraded,
        boolean reportAvailable,
        Progress progress,
        boolean replayed
) {

    public record Progress(int answeredRounds, int currentRound) {
    }
}
