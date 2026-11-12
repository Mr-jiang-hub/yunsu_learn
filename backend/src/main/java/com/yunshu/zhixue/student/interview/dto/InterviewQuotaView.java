package com.yunshu.zhixue.student.interview.dto;

/**
 * 今日面试配额视图（04 §6.7.2）；`quotaDate` 为 Asia/Shanghai 业务日，`resetsAt` 为次日 00:00 的 UTC 时间。
 */
public record InterviewQuotaView(
        String quotaDate,
        int limit,
        int used,
        int remaining,
        String resetsAt,
        String serverTime
) {
}
