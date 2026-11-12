package com.yunshu.zhixue.student.practice.dto;

import java.util.List;

/**
 * 判分结果（04 §6.5.3 / §6.5.4）：练习详情、作答响应、结果页共用。
 *
 * <p>`judgeStatus` 取值 `JUDGED`（已判分）/ `PENDING_SELF_ASSESSMENT`（AI 降级待自评）/
 * `SELF_ASSESSED`（已自评）；降级时 `scored=false`、`score=null`，不能当作 0 分。
 * `correctOptionIds` 与解析只在该题作答后返回，未作答绝不返回。</p>
 */
public record JudgeView(
        long questionId,
        String judgeStatus,
        boolean scored,
        boolean degraded,
        Boolean isCorrect,
        Double score,
        List<Long> correctOptionIds,
        String analysis,
        String referenceAnswer,
        List<HitPoint> hitPoints,
        String answeredAt
) {

    public record HitPoint(String point, boolean hit) {
    }
}
