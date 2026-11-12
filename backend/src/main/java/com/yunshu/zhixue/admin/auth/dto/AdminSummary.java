package com.yunshu.zhixue.admin.auth.dto;

/** B-01 主体摘要（04 §6.10.1） */
public record AdminSummary(
        long adminId,
        String username,
        String displayName,
        long roleId,
        String roleCode,
        String roleName
) {
}
