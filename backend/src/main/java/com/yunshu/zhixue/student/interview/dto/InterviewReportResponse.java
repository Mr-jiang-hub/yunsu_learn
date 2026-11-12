package com.yunshu.zhixue.student.interview.dto;

import java.util.List;

/**
 * 面试报告（04 §6.7.9）。`status="INCOMPLETE"` 表示提前结束/追问失败，前端必须标注"报告不完整"。
 */
public record InterviewReportResponse(
        long sessionId,
        String status,
        Double totalScore,
        String summary,
        List<String> suggestions,
        List<DimensionScore> dimensions,
        String generatedAt
) {

    public record DimensionScore(long dimensionId, String dimensionName, double score) {
    }
}
