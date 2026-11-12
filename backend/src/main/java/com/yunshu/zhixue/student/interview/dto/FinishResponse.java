package com.yunshu.zhixue.student.interview.dto;

/** 提前结束结果（04 §6.7.8）；重复调用返回首次结果。 */
public record FinishResponse(
        long sessionId,
        String status,
        String finishedAt,
        int answeredRounds,
        boolean reportAvailable
) {
}
