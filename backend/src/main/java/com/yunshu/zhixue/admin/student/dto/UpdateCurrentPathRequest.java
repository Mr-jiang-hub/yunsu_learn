package com.yunshu.zhixue.admin.student.dto;

import jakarta.validation.constraints.NotNull;

/** 调整学员当前主路线（04 §6.10.7）：幂等，不删除历史进度 */
public record UpdateCurrentPathRequest(
        @NotNull(message = "路线不能为空")
        Long pathId
) {
}
