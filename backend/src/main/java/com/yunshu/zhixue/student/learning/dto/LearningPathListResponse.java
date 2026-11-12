package com.yunshu.zhixue.student.learning.dto;

import java.util.List;

/** 路线列表与当前主路线（04 §6.3）。 */
public record LearningPathListResponse(
        List<LearningPathCard> items,
        Long currentPathId
) {
}
