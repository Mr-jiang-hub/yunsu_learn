package com.yunshu.zhixue.admin.student.dto;

/** 学员注销（04 §6.10.7）：需二次确认，注销后手机号释放、历史保留 */
public record CancelStudentRequest(
        String confirmText,
        String reason
) {
}
