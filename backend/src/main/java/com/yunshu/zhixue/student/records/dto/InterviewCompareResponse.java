package com.yunshu.zhixue.student.records.dto;

import java.util.List;

/**
 * 两次面试对比（04 §6.8）：仅允许同一学科、两份 `NORMAL` 报告，否则 30052。
 * 维度分可能在某一侧缺失（维度后来新增/停用），故用可空分数。
 */
public record InterviewCompareResponse(
        Side base,
        Side target,
        List<DimensionDelta> dimensions,
        Double totalDelta
) {

    public record Side(long sessionId, String subjectName, String finishedAt, Double totalScore) {
    }

    public record DimensionDelta(
            long dimensionId,
            String dimensionName,
            Double baseScore,
            Double targetScore,
            Double delta
    ) {
    }
}
