package com.yunshu.zhixue.admin.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 全量覆盖角色模块（04 §6.10.9）：super_admin 不可改，模块编码须在白名单内 */
public record UpdateRoleModulesRequest(
        @NotNull(message = "模块列表不能为空")
        List<String> moduleCodes
) {
}
