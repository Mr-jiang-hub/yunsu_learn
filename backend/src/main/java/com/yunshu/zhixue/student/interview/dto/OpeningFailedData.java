package com.yunshu.zhixue.student.interview.dto;

/**
 * 开场题生成失败（HTTP 502 + 50002）时的 data：会话已作废、次数已返还，前端提示"创建失败，次数已返还"。
 */
public record OpeningFailedData(
        long sessionId,
        boolean refunded,
        InterviewQuotaView quota
) {
}
