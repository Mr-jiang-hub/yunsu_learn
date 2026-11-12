package com.yunshu.zhixue.student.profile.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料（04 §6.9）：只允许昵称与头像，禁止 status/phone/password；
 * 未知字段由 Jackson 拒绝（10001）。
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50, message = "昵称长度需为 1-50 个字符") String nickname,
        String avatarKey
) {

    public boolean isEmpty() {
        return (nickname == null || nickname.isBlank()) && (avatarKey == null || avatarKey.isBlank());
    }
}
