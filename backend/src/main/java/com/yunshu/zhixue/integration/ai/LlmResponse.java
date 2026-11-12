package com.yunshu.zhixue.integration.ai;

/**
 * 大模型返回。degraded=true 表示调用失败或超时后返回的降级结果，
 * 业务层必须用状态标记区分“失败”和“得 0 分”。
 */
public record LlmResponse(
        String content,
        String model,
        int promptTokens,
        int completionTokens,
        boolean degraded
) {
}
