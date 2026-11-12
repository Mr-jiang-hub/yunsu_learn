package com.yunshu.zhixue.integration.sms;

import com.yunshu.zhixue.common.util.MaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 开发环境短信 mock：只打印脱敏手机号与验证码，不调用阿里云。
 * 生产 Profile 会因 provider != mock 且缺少真实适配器而启动失败。
 */
@Configuration
@ConditionalOnProperty(prefix = "integration.sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsConfig {

    private static final Logger log = LoggerFactory.getLogger(MockSmsConfig.class);

    @Bean
    public SmsSender mockSmsSender() {
        return (phone, code, scene) -> log.warn("[dev mock 短信] phone={}, scene={}, code={}",
                MaskUtils.maskPhone(phone), scene, code);
    }
}
