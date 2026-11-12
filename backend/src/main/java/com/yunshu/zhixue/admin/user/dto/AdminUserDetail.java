package com.yunshu.zhixue.admin.user.dto;

/** 后台账号详情（04 §6.10.8） */
public record AdminUserDetail(
        long id,
        String username,
        String displayName,
        long roleId,
        String roleName,
        String roleCode,
        String status,
        boolean mustChangePassword,
        String lastLoginAt,
        String createdAt,
        String updatedAt
) {
}
