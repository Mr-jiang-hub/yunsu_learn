package com.yunshu.zhixue.student.learning.dto;

import java.util.List;

/** 阶段详情（04 §6.4）：课程列表 + 阶段进度 + 练习可用性。 */
public record StageDetailResponse(
        StageBrief stage,
        List<StageCourseItem> courses,
        Progress progress,
        PracticeAvailability practice
) {

    public record StageBrief(
            long stageId,
            String stageName,
            long pathId,
            String pathName,
            int sortNo,
            String status
    ) {
    }

    public record Progress(
            int completedCourseCount,
            int courseCount,
            double progressRate,
            double masteryRate,
            boolean hasPracticeSample
    ) {
    }

    public record PracticeAvailability(
            boolean canStart,
            int questionCount,
            String reason
    ) {
    }
}
