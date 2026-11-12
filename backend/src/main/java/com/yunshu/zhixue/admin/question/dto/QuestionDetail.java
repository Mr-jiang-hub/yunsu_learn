package com.yunshu.zhixue.admin.question.dto;

import java.util.List;

/** 题目完整详情（04 §6.10.4 / 6.10.4.1） */
public record QuestionDetail(
        long id,
        String type,
        long subjectId,
        String subjectName,
        long stageId,
        String stageName,
        String content,
        List<OptionItem> options,
        List<ScoringPointItem> scoringPoints,
        String analysis,
        String referenceAnswer,
        String status,
        String createdAt,
        String updatedAt
) {

    public record OptionItem(
            long optionId,
            String optionKey,
            String content,
            boolean isCorrect,
            int sortNo
    ) {
    }

    public record ScoringPointItem(
            long scoringPointId,
            String pointText,
            int sortNo
    ) {
    }
}
