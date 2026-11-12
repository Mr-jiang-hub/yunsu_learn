package com.yunshu.zhixue.admin.auth.dto;

import java.util.List;

/**
 * B-01 GET /api/v1/admin/auth/me 成功响应（04 §6.10.1）：
 * 前端刷新页面时据此恢复菜单与权限。
 */
public record AdminMeResponse(
        AdminSummary admin,
        AdminRoleBrief role,
        List<String> modules
) {
}
