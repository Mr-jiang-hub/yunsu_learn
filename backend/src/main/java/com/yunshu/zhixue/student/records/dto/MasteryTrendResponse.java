package com.yunshu.zhixue.student.records.dto;

import java.util.List;

/**
 * 掌握度趋势（04 §6.8）：只取 `STAGE` 且 `FINISHED` 的阶段练习，按完成时间升序。
 * 错题重练不进入趋势。
 */
public record MasteryTrendResponse(
        long stageId,
        String stageName,
        List<Point> points
) {

    public record Point(long practiceId, String finishedAt, double correctRate, double masteryRate) {
    }
}
