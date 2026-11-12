package com.yunshu.zhixue.student.learning.dto;

/** 切换主路线结果（04 §6.3）。 */
public record CurrentPathResponse(
        long currentPathId,
        String pathName,
        String updatedAt
) {
}
