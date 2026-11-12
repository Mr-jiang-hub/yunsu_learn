package com.yunshu.zhixue.student.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码（04 §6.9 + 评审缺口 G-02）：成功后撤销全部 refresh token，要求重新登录。
 *
 * <p>PRD 5.10 要求"原密码或验证码二选一"，所以两个凭证字段都可选，
 * 由服务端在方法内做"恰好一个"的校验——Bean Validation 表达不了这种互斥，
 * 不能在这里加 {@code @NotBlank}，否则验证码分支会被卡死。</p>
 */
public record ChangePasswordRequest(
        @Size(max = 32, message = "原密码长度需为 8-32 位")
        String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度需为 8-32 位")
        String newPassword,
        @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
        String smsCode
) {
}
