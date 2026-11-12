package com.yunshu.zhixue.student.practice.dto;

/**
 * 提交/结束练习（04 §6.5.6）。`masteryEligible=false` 针对错题重练：
 * 不计入阶段掌握度，`masteryRate` 为 null。
 */
public record PracticeSubmitResponse(
        long practiceId,
        String status,
        int questionCount,
        int answeredCount,
        int correctCount,
        double correctRate,
        long durationSeconds,
        Double masteryRate,
        boolean masteryEligible,
        String finishedAt
) {
}
