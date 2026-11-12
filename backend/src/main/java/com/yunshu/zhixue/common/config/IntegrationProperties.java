package com.yunshu.zhixue.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 外部依赖适配器配置。P0 的 LLM / ASR / SMS 在业务开发阶段接入真实实现，
 * 基础工程先提供 mock，生产 Profile 启动时会拒绝 mock。
 */
@ConfigurationProperties(prefix = "integration")
public record IntegrationProperties(Ai ai, Sms sms) {

    public record Ai(String provider, Duration connectTimeout, Duration requestTimeout) {
        public boolean isMock() {
            return "mock".equalsIgnoreCase(provider);
        }
    }

    public record Sms(String provider) {
        public boolean isMock() {
            return "mock".equalsIgnoreCase(provider);
        }
    }
}
