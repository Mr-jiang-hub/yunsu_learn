package com.yunshu.zhixue.student.wrongbook.dto;

import java.util.List;

/** 错题详情（04 §6.6）：含正确答案与解析，仅本人错题本内可见。 */
public record WrongQuestionDetail(
        QuestionDetail question,
        MyLastAnswer myLastAnswer,
        List<Long> correctOptionIds,
        String referenceAnswer,
        String analysis,
        List<String> scoringPoints,
        String lastWrongAt,
        String addedAt
) {

    public record QuestionDetail(
            long questionId,
            String type,
            String content,
            List<OptionDetail> options
    ) {
    }

    public record OptionDetail(long optionId, String optionKey, String content, boolean isCorrect) {
    }

    public record MyLastAnswer(List<Long> selectedOptionIds, String answerText) {
    }
}
