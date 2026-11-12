package com.yunshu.zhixue.student.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** C-01 密码登录请求（04 §6.1.3） */
public record PasswordLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
