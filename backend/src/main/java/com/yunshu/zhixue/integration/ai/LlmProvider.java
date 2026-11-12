package com.yunshu.zhixue.integration.ai;

/**
 * 大模型端口：简答判分、面试出题、面试评分/报告。
 * 业务层只依赖本接口，提示词与评分规则留在应用层（02 §2.7）。
 */
public interface LlmProvider {

    LlmResponse complete(LlmRequest request);
}
