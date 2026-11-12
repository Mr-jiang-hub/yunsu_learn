package com.yunshu.zhixue.student.learning.dto;

/**
 * 课程进度（04 §6.4 start / complete 的响应）。
 *
 * <p>`bilibiliUrl` 为评审缺口 G-01 补的字段：开始学习后前端要立即跳 B 站，
 * 不能让客户端自己拼 URL，由服务端从 `course.bilibili_url` 读出并下发。</p>
 */
public record CourseProgressResponse(
        long courseId,
        String bilibiliUrl,
        String progressStatus,
        String startedAt,
        String completedAt
) {
}
