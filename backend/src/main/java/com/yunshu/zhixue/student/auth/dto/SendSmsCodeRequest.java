package com.yunshu.zhixue.student.auth.dto;

import com.yunshu.zhixue.student.auth.SmsPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** C-01 发送验证码请求（04 §6.1.1） */
public record SendSmsCodeRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @NotNull(message = "验证码用途不能为空")
        SmsPurpose purpose
) {
}
