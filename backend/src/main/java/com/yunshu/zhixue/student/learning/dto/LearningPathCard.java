package com.yunshu.zhixue.student.learning.dto;

/** 路线卡片（04 §6.3）：`progressRate` / `masteryRate` 为 0–100 的百分数。 */
public record LearningPathCard(
        long pathId,
        String pathName,
        long subjectId,
        String subjectName,
        int stageCount,
        int courseCount,
        int questionCount,
        double progressRate,
        double masteryRate,
        boolean isCurrent
) {
}
