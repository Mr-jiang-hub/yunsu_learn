package com.yunshu.zhixue.admin.auth.dto;

/** B-01 登录响应；refresh 经 Set-Cookie（ys_b_rt）下发，不进响应体 */
public record AdminAuthSessionResponse(
        String accessToken,
        String accessExpiresAt,
        String refreshExpiresAt,
        AdminSummary admin,
        java.util.List<String> modules,
        boolean mustChangePassword
) {
}
