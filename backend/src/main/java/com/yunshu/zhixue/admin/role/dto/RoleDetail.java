package com.yunshu.zhixue.admin.role.dto;

import java.util.List;

/** 角色详情（04 §6.10.9）：modules 为模块编码列表 */
public record RoleDetail(
        long id,
        String code,
        String name,
        String description,
        List<String> modules
) {
}
