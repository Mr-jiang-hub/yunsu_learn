package com.yunshu.zhixue.admin.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建消息：落库即为草稿，需另行发送才会投递给学员。
 *
 * @param type     类型：SYSTEM 系统通知 / STUDY 学习提醒 / ACTIVITY 活动通知
 * @param audience 受众：ALL 全体学员 / SPECIFIED 指定学员
 * @param studentIds audience=SPECIFIED 时必填，最多 200 个；注销学员发送时会被剔除
 */
public record CreateMessageRequest(
        @NotBlank(message = "消息标题不能为空")
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
