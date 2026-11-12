package com.yunshu.zhixue.student.home.dto;

import java.util.List;

/**
 * C 端首页聚合（04 §6.2.1）。
 *
 * <p>区块级降级：单个区块取数失败不影响整体 200，
 * 该区块置 null 并在 {@code meta.degradedSections} 记录失败码。</p>
 */
public record HomeResponse(
        boolean hasPath,
        CurrentPathSection currentPath,
        ContinueLearning continueLearning,
        PracticeSection practice,
        InterviewSection interview,
        List<ActivityItem> recentActivities,
        List<NoticeItem> notices,
        Meta meta
) {

    /** 主路线概览；masteryRate 无阶段练习样本时为 0，用 hasPracticeSample 区分 */
    public record CurrentPathSection(
            long pathId,
            String pathName,
            String subjectName,
            int stageCount,
            int completedStageCount,
            double progressRate,
            double masteryRate,
            boolean hasPracticeSample
    ) {
    }

    /** 继续学习：优先回到"已开始未完成"的课程，没有则给阶段内第一门未开始课程 */
    public record ContinueLearning(
            long stageId,
            String stageName,
            long courseId,
            String courseName,
            String progressStatus
    ) {
    }

    /** 随堂练习入口：阶段是否有题、是否有未完成练习、最近一次完成时间 */
    public record PracticeSection(
            long stageId,
            String stageName,
            boolean canStart,
            Long inProgressPracticeId,
            String lastFinishedAt
    ) {
    }

    /** 面试入口：今日次数与最近一次可用报告 */
    public record InterviewSection(
            String quotaDate,
            int limit,
            int used,
            int remaining,
            boolean canStart,
            Long lastReportId
    ) {
    }

    /** 最近动态：由课程完成/练习完成/面试完成派生，不建 student_activity 表（04-Q46） */
    public record ActivityItem(String type, String title, String occurredAt) {
    }

    /** 公告：只返回已发布且未失效的前 3 条；pinned 仅影响排序，不额外占用配额 */
    public record NoticeItem(long noticeId, String title, String content, boolean pinned, String publishedAt) {
    }

    public record DegradedSection(String section, int code, String message) {
    }

    public record Meta(String dataAsOf, int cacheTtlSeconds, List<DegradedSection> degradedSections) {
    }
}
