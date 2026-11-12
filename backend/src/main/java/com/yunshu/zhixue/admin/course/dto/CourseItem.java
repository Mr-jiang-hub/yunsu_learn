package com.yunshu.zhixue.admin.course.dto;

/** 课程列表/详情（04 §6.10.3）；list 不返回 description */
public record CourseItem(
        long id,
        String title,
        long subjectId,
        String subjectName,
        long stageId,
        String stageName,
        String bilibiliUrl,
        String coverUrl,
        Integer durationSeconds,
        int sortNo,
        String status,
        String description,
        String createdAt,
        String updatedAt
) {
}
