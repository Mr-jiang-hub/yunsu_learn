package com.yunshu.zhixue.student.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 换绑手机号（04 §6.9）：原号与新号双重验证码，成功后撤销全部 refresh token。
 */
public record PhoneChangeRequest(
        @NotBlank(message = "原手机号验证码不能为空") String oldCode,
        @NotBlank(message = "新手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String newPhone,
        @NotBlank(message = "新手机号验证码不能为空") String newCode
) {
}
