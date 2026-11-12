package com.yunshu.zhixue.admin.message.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑消息（部分更新，字段为 null 表示不修改）；仅草稿可编辑。
 *
 * <p>受众从 ALL 改成 SPECIFIED 时必须同时给 studentIds；
 * 从 SPECIFIED 改成 ALL 时 studentIds 会被忽略。</p>
 */
public record UpdateMessageRequest(
        @Size(max = 100, message = "消息标题不能超过 100 字符")
        String title,
        @Size(max = 2000, message = "消息正文不能超过 2000 字符")
        String content,
        @Pattern(regexp = "SYSTEM|STUDY|ACTIVITY", message = "消息类型只能是 SYSTEM、STUDY 或 ACTIVITY")
        String type,
        @Pattern(regexp = "ALL|SPECIFIED", message = "受众只能是 ALL 或 SPECIFIED")
        String audience,
        List<Long> studentIds
) {
}
