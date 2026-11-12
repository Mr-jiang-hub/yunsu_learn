package com.yunshu.zhixue.student.records.dto;

/** 已完成课程记录（04 §6.8）：只返回 `COMPLETED` 课程，按完成时间倒序。 */
public record CourseRecordItem(
        long courseId,
        String title,
        String subjectName,
        String stageName,
        String startedAt,
        String completedAt
) {
}
