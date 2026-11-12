package com.yunshu.zhixue.student.auth.dto;

/** C-01 发送验证码响应（04 §6.1.1） */
public record SendSmsCodeResponse(
        boolean sent,
        int expireSeconds,
        int retryAfterSeconds,
        int remainingToday
) {
}
