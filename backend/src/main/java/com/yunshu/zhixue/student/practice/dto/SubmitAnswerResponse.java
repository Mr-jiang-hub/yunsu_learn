package com.yunshu.zhixue.student.practice.dto;

/**
 * 作答响应（04 §6.5.4）。`replayed=true` 表示重复提交被识别，返回的是首次判分结果，
 * 未重复判分、未产生第二次 AI 调用。
 */
public record SubmitAnswerResponse(
        JudgeView answer,
        Progress progress,
        PracticeStartResponse.QuestionView nextQuestion,
        boolean replayed
) {

    public record Progress(
            int answeredCount,
            int remainingCount,
            int currentIndex,
            String practiceStatus
    ) {
    }
}
