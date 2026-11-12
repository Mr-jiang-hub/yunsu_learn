package com.yunshu.zhixue.admin.notice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建公告：落库即为草稿，需另行发布才会出现在 C 端。
 *
 * @param expireAt 失效时间，RFC 3339（如 2026-12-31T16:00:00Z）；为空表示长期有效
 */
public record CreateNoticeRequest(
        @NotBlank(message = "公告标题不能为空")
        @Size(max = 100, message = "公告标题不能超过 100 字符")
        String title,
        @Size(max = 2000, message = "公告正文不能超过 2000 字符")
        String content,
        Boolean pinned,
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo,
        String expireAt
) {
}
