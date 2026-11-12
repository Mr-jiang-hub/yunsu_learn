package com.yunshu.zhixue.student.interview.dto;

import java.util.List;

/**
 * 面试入口选项（04 §6.7.1）。`items` 只包含当前主路线下的启用学科；无主路线时为空数组。
 */
public record InterviewOptionsResponse(
        List<SubjectOption> items,
        String quotaDate,
        String serverTime
) {

    public record SubjectOption(
            long subjectId,
            String subjectName,
            Long pathId,
            String pathName,
            Long stageId,
            String stageName,
            int dimensionCount,
            List<DifficultyOption> difficulties,
            boolean canStart,
            String reasonCode,
            String reasonMessage,
            int remainingQuota
    ) {
    }

    public record DifficultyOption(
            long difficultyId,
            String code,
            String name,
            String description,
            String focus,
            String status
    ) {
    }
}
