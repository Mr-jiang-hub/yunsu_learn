package com.yunshu.zhixue.student.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 给「本人已绑定手机号」发验证码（换绑原号验证 / 注销确认 / 已登录改密）。
 * 只接受这三种用途，其余用途仍走 `/c/auth/sms-codes`。
 *
 * <p>`CHANGE_PASSWORD` 是评审缺口 G-02 加入的：PRD 5.10 允许用验证码改密，
 * 而前端只有掩码手机号，必须由服务端按 token 取号下发。</p>
 */
public record SelfSmsCodeRequest(
        @NotBlank(message = "用途不能为空")
        @Pattern(regexp = "CHANGE_PHONE_OLD|CANCEL|CHANGE_PASSWORD", message = "用途不支持")
        String purpose
) {
}
