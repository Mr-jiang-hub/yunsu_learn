package com.yunshu.zhixue.student.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** C-01 注册请求（04 §6.1.2）；密码规则 8-32 位含字母和数字由服务层校验返回 30005 */
public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @NotBlank(message = "验证码不能为空")
        String code,
        @NotBlank(message = "密码不能为空")
        String password,
        @Size(max = 20, message = "昵称不能超过 20 字符")
        String nickname
) {
}
