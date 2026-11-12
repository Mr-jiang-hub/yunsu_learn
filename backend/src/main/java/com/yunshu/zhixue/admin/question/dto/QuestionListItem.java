package com.yunshu.zhixue.admin.question.dto;

/** 题目列表项（04 §6.10.4）：不返回完整选项/解析，题干只给预览 */
public record QuestionListItem(
        long id,
        String type,
        String contentPreview,
        long subjectId,
        String subjectName,
        long stageId,
        String stageName,
        String status,
        String createdAt,
        String updatedAt
) {
}
