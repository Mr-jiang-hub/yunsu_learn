package com.yunshu.zhixue.admin.path.dto;

/** 学习路线列表项（04 §6.10.6） */
public record LearningPathItem(
        long id,
        String name,
        long subjectId,
        String subjectName,
        String description,
        String status,
        int stageCount,
        String createdAt,
        String updatedAt
) {
}
