package com.yunshu.zhixue.student.learning.dto;

import jakarta.validation.constraints.NotNull;

/** 设置/切换主路线请求（04 §6.3）。 */
public record UpdateCurrentPathRequest(
        @NotNull(message = "路线不能为空")
        Long pathId
) {
}
