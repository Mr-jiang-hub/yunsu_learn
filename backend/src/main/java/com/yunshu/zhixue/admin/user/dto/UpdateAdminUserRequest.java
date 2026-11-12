package com.yunshu.zhixue.admin.user.dto;

import jakarta.validation.constraints.Size;

/** 编辑后台账号（04 §6.10.8）：禁止改 status / password / username */
public record UpdateAdminUserRequest(
        @Size(max = 50, message = "姓名不能超过 50 字符")
        String displayName,
        Long roleId
) {
}
