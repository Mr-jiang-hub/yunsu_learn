package com.yunshu.zhixue.admin.message.dto;

/**
 * 后台消息条目（模块 message）。
 *
 * @param status   DRAFT 草稿 / SENT 已发送 / REVOKED 已撤回
 * @param audience ALL 全体学员 / SPECIFIED 指定学员
 * @param type     SYSTEM 系统通知 / STUDY 学习提醒 / ACTIVITY 活动通知
 * @param targetCount 发送时接收人数快照；草稿为 0
 * @param readCount   已读人数（收件行 read_at 非空且未删除）；草稿为 0
 */
public record MessageItem(
        long id,
        String title,
        String content,
        String type,
        String audience,
        String status,
        int targetCount,
        int readCount,
        String sentAt,
        String createdAt,
        String updatedAt
) {
}
