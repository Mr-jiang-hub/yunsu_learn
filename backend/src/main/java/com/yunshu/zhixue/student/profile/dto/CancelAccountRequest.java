package com.yunshu.zhixue.student.profile.dto;

import jakarta.validation.constraints.NotBlank;

/** 注销账号（04 §6.9）：`confirmText` 必须为「注销」，验证码发到当前手机号。 */
public record CancelAccountRequest(
        @NotBlank(message = "验证码不能为空") String code,
        @NotBlank(message = "请输入确认文本") String confirmText
) {
}
