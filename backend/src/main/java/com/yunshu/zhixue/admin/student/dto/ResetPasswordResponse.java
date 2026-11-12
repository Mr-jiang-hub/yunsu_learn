package com.yunshu.zhixue.admin.student.dto;

/** 重置密码结果（04 §6.10.7）：明文仅在本次响应返回一次 */
public record ResetPasswordResponse(
        String temporaryPassword,
        boolean mustChangePassword,
        String resetAt
) {
}
