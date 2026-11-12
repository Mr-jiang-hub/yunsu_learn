package com.yunshu.zhixue.admin.notice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 编辑公告（部分更新，字段为 null 表示不修改）。
 *
 * <p>失效时间需要区分「不修改」与「改为长期有效」两种意图，
 * 因此除 {@code expireAt} 外另给 {@code clearExpire}：
 * 传 expireAt 时以其为准；只传 clearExpire=true 时清空；两者都不传则不修改。</p>
 */
public record UpdateNoticeRequest(
        @Size(max = 100, message = "公告标题不能超过 100 字符")
        String title,
        @Size(max = 2000, message = "公告正文不能超过 2000 字符")
        String content,
        Boolean pinned,
        @Min(value = 0, message = "排序号不能为负")
        Integer sortNo,
        String expireAt,
        Boolean clearExpire
) {
}
