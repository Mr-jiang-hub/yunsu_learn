package com.yunshu.zhixue.student.practice.dto;

import java.util.List;

/** 练习详情与断点续答（04 §6.5.3）。 */
public record PracticeDetailResponse(
        PracticeInfo practice,
        PracticeStartResponse.QuestionView currentQuestion,
        List<AnsweredItem> answered,
        boolean canSubmit
) {

    public record PracticeInfo(
            long practiceId,
            String type,
            String status,
            Long stageId,
            String stageName,
            String startedAt,
            String expireAt,
            int questionCount,
            int answeredCount,
            int remainingCount,
            int currentIndex
    ) {
    }

    public record AnsweredItem(
            long questionId,
            MyAnswer myAnswer,
            JudgeView judge
    ) {
    }

    public record MyAnswer(List<Long> selectedOptionIds, String answerText) {
    }
}
