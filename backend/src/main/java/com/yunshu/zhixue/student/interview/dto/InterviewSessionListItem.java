package com.yunshu.zhixue.student.interview.dto;

/**
 * 面试历史列表项（04 §6.7.4）。不返回报告正文；`reportStatus` 在报告未生成时为 null。
 */
public record InterviewSessionListItem(
        long sessionId,
        long subjectId,
        String subjectName,
        String difficultyCode,
        String difficultyName,
        String stageName,
        String status,
        int currentRound,
        String startedAt,
        String finishedAt,
        Double totalScore,
        String reportStatus,
        String quotaDate
) {
}
