package com.yunshu.zhixue.admin.notice.dto;

/**
 * 后台公告条目（模块 notice）。
 *
 * @param status DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线
 */
public record NoticeItem(
        long id,
        String title,
        String content,
        String status,
        boolean pinned,
        int sortNo,
        String publishedAt,
        String expireAt,
        String createdAt,
        String updatedAt
) {
}
