package com.yunshu.zhixue.admin.path.dto;

import java.util.List;

/** 学习路线详情 + 阶段列表（04 §6.10.6，供课程/题目表单选择阶段） */
public record LearningPathDetail(
        long id,
        String name,
        long subjectId,
        String subjectName,
        String description,
        String status,
        List<StageItem> stages,
        String createdAt,
        String updatedAt
) {

    public record StageItem(
            long id,
            String name,
            String description,
            int sortNo,
            String status,
            int courseCount,
            int questionCount
    ) {
    }
}
