package com.yunshu.zhixue.admin.user.dto;

/** 后台账号列表项（04 §6.10.8）：不返回密码哈希 */
public record AdminUserItem(
        long id,
        String username,
        String displayName,
        long roleId,
        String roleName,
        String status,
        String lastLoginAt,
        String createdAt
) {
}
