package com.yunshu.zhixue.student.auth.dto;

/** C 端登录/注册/验证码登录响应；refresh 经 Set-Cookie 下发，不进响应体（04 §4.2） */
public record AuthSessionResponse(
        String accessToken,
        String accessExpiresAt,
        String refreshExpiresAt,
        StudentSummary student
) {
}
