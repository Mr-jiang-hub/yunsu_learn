package com.yunshu.zhixue.student.learning.dto;

/**
 * 阶段详情中的课程项（04 §6.4）。已开始但停用的课程仍返回，`status=DISABLED`。
 *
 * <p>`bilibiliUrl` 是评审缺口 G-01 补的：PRD 5.4 要求课程内容含 B 站视频链接，
 * 点击开始学习后要跳转，而原契约只在 `course.bilibili_url` 存了值、C 端读不到，
 * 课程学习闭环断在这里。字段只读，不新增表。</p>
 */
public record StageCourseItem(
        long courseId,
        String title,
        String bilibiliUrl,
        String coverUrl,
        Integer durationSeconds,
        int sortNo,
        String progressStatus,
        String startedAt,
        String completedAt,
        String status
) {
}
