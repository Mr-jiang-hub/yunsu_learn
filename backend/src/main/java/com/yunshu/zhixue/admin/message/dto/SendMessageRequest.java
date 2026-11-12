package com.yunshu.zhixue.admin.message.dto;

import java.util.List;

/**
 * 发送消息时的定向名单；仅 audience=SPECIFIED 时需要，全体消息忽略该字段。
 */
public record SendMessageRequest(List<Long> studentIds) {
}
