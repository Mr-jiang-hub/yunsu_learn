package com.yunshu.zhixue.admin.student.dto;

import java.util.List;

/**
 * 学员详情（04 §6.10.7）。掌握度取"最近一次已完成阶段练习"的 correct_rate（03 §7.2.1），
 * 完成度按 course_progress 与当前路线启用课程数实时计算，均不落冗余列。
 */
public record StudentDetail(
        Summary student,
        Progress progress,
        List<Activity> recentActivities,
        InterviewSummary interviewSummary
) {

    public record Summary(
            long id,
            String nickname,
            String phoneMasked,
            String avatarUrl,
            String status,
            PathRef currentPath,
            String createdAt,
            String lastLoginAt,
            String cancelledAt
    ) {
    }

    public record PathRef(long id, String name) {
    }

    public record Progress(
            int completedCourses,
            int courseCount,
            Double masteryRate,
            Integer masteryQuestionCount,
            String masteryAt,
            String lastActiveAt
    ) {
    }

    /** 最近动态：type ∈ COURSE / PRACTICE / INTERVIEW */
    public record Activity(String type, String title, String detail, String at) {
    }

    public record InterviewSummary(int total, int completed, String lastFinishedAt) {
    }
}
