package com.yunshu.zhixue.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** B-01 后台登录请求（04 §6.10.1） */
public record AdminLoginRequest(
        @NotBlank(message = "账号不能为空")
        String username,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
