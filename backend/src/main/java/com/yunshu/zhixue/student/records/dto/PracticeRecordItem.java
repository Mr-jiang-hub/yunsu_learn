package com.yunshu.zhixue.student.records.dto;

/**
 * 练习记录项（04 §6.8）：包含阶段练习与错题重练；错题重练 `masteryEligible=false`
 * 且 `masteryRate=null`，不计入掌握度。
 */
public record PracticeRecordItem(
        long practiceId,
        String type,
        String status,
        String stageName,
        int questionCount,
        int correctCount,
        double correctRate,
        Double masteryRate,
        boolean masteryEligible,
        String startedAt,
        String finishedAt
) {
}
