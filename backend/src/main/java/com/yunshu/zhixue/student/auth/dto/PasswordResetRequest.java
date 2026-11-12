package com.yunshu.zhixue.student.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** C-01 重置密码请求（04 §6.1.5），code 用途 = RESET_PASSWORD */
public record PasswordResetRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @NotBlank(message = "验证码不能为空")
        String code,
        @NotBlank(message = "新密码不能为空")
        String newPassword
) {
}
