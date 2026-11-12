package com.yunshu.zhixue.admin.auth.dto;

/** B-01 /me 中的角色摘要（04 §6.10.1） */
public record AdminRoleBrief(
        long roleId,
        String roleCode,
        String roleName
) {
}
