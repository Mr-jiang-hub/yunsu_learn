package com.yunshu.zhixue.admin.role.dto;

/** 角色列表项（04 §6.10.9）：固定 3 个预置角色，仅内置 */
public record RoleItem(
        long id,
        String code,
        String name,
        String description,
        int moduleCount,
        boolean builtIn
) {
}
