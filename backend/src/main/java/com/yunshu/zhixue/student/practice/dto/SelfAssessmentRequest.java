package com.yunshu.zhixue.student.practice.dto;

import jakarta.validation.constraints.NotNull;

/** 简答降级后的学员自评（04 §6.5.5）。 */
public record SelfAssessmentRequest(
        @NotNull(message = "请选择是否正确")
        Boolean isCorrect
) {
}
