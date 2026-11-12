package com.yunshu.zhixue.admin.student.dto;

/** 学员列表项（04 §6.10.7）：手机号仅返回脱敏值，精确搜索走后端盲索引 */
public record StudentListItem(
        long id,
        String nickname,
        String phoneMasked,
        String status,
        String currentPathName,
        String lastLoginAt,
        String createdAt
) {
}
