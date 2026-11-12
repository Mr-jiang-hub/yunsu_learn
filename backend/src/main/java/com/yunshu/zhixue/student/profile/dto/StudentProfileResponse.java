package com.yunshu.zhixue.student.profile.dto;

/**
 * C 端个人资料响应（04 §6 第 37 号接口 GET /c/students/me）。
 * 不返回完整手机号；avatarUrl 为空表示未设置头像。
 */
public record StudentProfileResponse(
        long studentId,
        String nickname,
        String phoneMasked,
        String avatarUrl,
        int status,
        CurrentPath currentPath,
        boolean hasPassword,
        String createdAt
) {

    /** 当前主路线；未选择时为 null */
    public record CurrentPath(long pathId, String pathName) {
    }
}
