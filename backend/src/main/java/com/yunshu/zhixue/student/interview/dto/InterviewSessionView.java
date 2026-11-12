package com.yunshu.zhixue.student.interview.dto;

/**
 * 面试会话视图（04 §6.7.3 / §6.7.5）。`stageName` 为创建时快照，不随阶段改名变化。
 *
 * <p>`currentRound` 是**当前待答轮次**（1–4），与数据库 `current_round`（已完成作答轮次 0–4）
 * 的映射为：进行中 = `current_round + 1`；已结束固定为 4。</p>
 *
 * <p>`finishedAt` / `voidReason` 仅在详情页出现，列表与创建响应中为 null。</p>
 */
public record InterviewSessionView(
        long sessionId,
        long subjectId,
        String subjectName,
        long difficultyId,
        String difficultyCode,
        String difficultyName,
        long stageId,
        String stageName,
        String status,
        int currentRound,
        String quotaDate,
        String startedAt,
        String expireAt,
        String finishedAt,
        String voidReason
) {
}
