package com.yunshu.zhixue.student.interview.dto;

import jakarta.validation.constraints.NotNull;

/** 创建面试会话（04 §6.7.3）：学科与难度必填，配置合法性由服务层校验。 */
public record CreateSessionRequest(
        @NotNull(message = "学科不能为空") Long subjectId,
        @NotNull(message = "难度档位不能为空") Long difficultyId
) {
}
