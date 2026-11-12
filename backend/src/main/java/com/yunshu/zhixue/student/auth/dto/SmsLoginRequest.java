package com.yunshu.zhixue.student.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** C-01 验证码登录请求（04 §6.1.4），code 用途 = LOGIN */
public record SmsLoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @NotBlank(message = "验证码不能为空")
        String code
) {
}
