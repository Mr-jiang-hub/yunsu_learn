package com.yunshu.zhixue.admin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增后台账号（04 §6.10.8）：登录名唯一，首次登录强制改密 */
public record CreateAdminUserRequest(
        @NotBlank(message = "登录名不能为空")
        @Size(min = 4, max = 32, message = "登录名长度需在 4-32 之间")
        String username,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 字符")
        String displayName,
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 32, message = "初始密码长度需在 8-32 之间")
        String password,
        @NotNull(message = "角色不能为空")
        Long roleId
) {
}
