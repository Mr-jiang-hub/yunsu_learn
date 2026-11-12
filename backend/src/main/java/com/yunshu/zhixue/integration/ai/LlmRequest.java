package com.yunshu.zhixue.integration.ai;

import java.time.Duration;
import java.util.Map;

/**
 * 与厂商无关的补全请求。超时由调用方按能力传入（判分 8–10s，出题 10s）。
 */
public record LlmRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        Duration timeout,
        Map<String, Object> metadata
) {
    public LlmRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
