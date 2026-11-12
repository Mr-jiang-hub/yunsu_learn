package com.yunshu.zhixue.student.auth.dto;

/** C 端主体摘要（04 §6.1.2） */
public record StudentSummary(
        long id,
        String nickname,
        String phoneMasked,
        Long currentPathId,
        boolean hasPassword
) {
}
