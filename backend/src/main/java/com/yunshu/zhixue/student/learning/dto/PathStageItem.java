package com.yunshu.zhixue.student.learning.dto;

/** 路线详情中的阶段项（04 §6.3）。 */
public record PathStageItem(
        long stageId,
        String stageName,
        int sortNo,
        int courseCount,
        int completedCourseCount,
        int questionCount,
        double masteryRate,
        boolean hasPracticeSample,
        String status
) {
}
