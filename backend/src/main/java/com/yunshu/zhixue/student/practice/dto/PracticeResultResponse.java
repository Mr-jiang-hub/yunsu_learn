package com.yunshu.zhixue.student.practice.dto;

import java.util.List;

/** 练习结果（04 §6.5.7）。 */
public record PracticeResultResponse(
        long practiceId,
        String type,
        String status,
        Long stageId,
        String stageName,
        int questionCount,
        int answeredCount,
        int correctCount,
        double correctRate,
        Double masteryRate,
        boolean masteryEligible,
        long durationSeconds,
        String finishedAt,
        List<WrongItem> wrongQuestions
) {

    public record WrongItem(
            long questionId,
            String type,
            String content,
            PracticeDetailResponse.MyAnswer myAnswer,
            List<Long> correctOptionIds,
            String referenceAnswer,
            String analysis,
            Double score,
            boolean degraded
    ) {
    }
}
