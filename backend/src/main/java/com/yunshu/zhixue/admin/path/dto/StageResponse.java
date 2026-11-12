package com.yunshu.zhixue.admin.path.dto;

/**
 * 单个阶段（04 §6.10.6）：用于新增/编辑/启停阶段的返回体。
 * 字段名沿用文档的 stageId；路线详情内的阶段数组用 {@link LearningPathDetail.StageItem} 的 id。
 */
public record StageResponse(
        long stageId,
        long pathId,
        String name,
        String description,
        int sortNo,
        String status,
        int courseCount,
        int questionCount,
        String createdAt,
        String updatedAt
) {
}
