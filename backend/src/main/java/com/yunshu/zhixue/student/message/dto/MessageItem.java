package com.yunshu.zhixue.student.message.dto;

/**
 * C 端消息条目。
 *
 * @param type   SYSTEM 系统通知 / STUDY 学习提醒 / ACTIVITY 活动通知
 * @param sentAt 发送时间（UTC，RFC 3339）
 * @param readAt 已读时间；null=未读
 */
public record MessageItem(
        long messageId,
        String title,
        String content,
        String type,
        String sentAt,
        String readAt
) {
}
