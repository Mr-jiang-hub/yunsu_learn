package com.yunshu.zhixue.common.config;

import com.yunshu.zhixue.integration.ai.AsrProvider;
import com.yunshu.zhixue.integration.ai.LlmProvider;
import com.yunshu.zhixue.integration.ai.TtsProvider;
import com.yunshu.zhixue.integration.sms.SmsSender;
import com.yunshu.zhixue.integration.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动配置校验：缺失必填配置直接失败，不带着空配置运行（02 §6.6 / 06 第 6 章）。
 */
@Component
public class StartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final AppProperties app;
    private final StorageProperties storage;
    private final IntegrationProperties integration;
    private final Environment environment;
    private final ObjectProvider<LlmProvider> llmProvider;
    private final ObjectProvider<AsrProvider> asrProvider;
    private final ObjectProvider<TtsProvider> ttsProvider;
    private final ObjectProvider<SmsSender> smsSender;

    public StartupValidator(AppProperties app, StorageProperties storage,
                            IntegrationProperties integration, Environment environment,
                            ObjectProvider<LlmProvider> llmProvider,
                            ObjectProvider<AsrProvider> asrProvider,
                            ObjectProvider<TtsProvider> ttsProvider,
                            ObjectProvider<SmsSender> smsSender) {
        this.app = app;
        this.storage = storage;
        this.integration = integration;
        this.environment = environment;
        this.llmProvider = llmProvider;
        this.asrProvider = asrProvider;
        this.ttsProvider = ttsProvider;
        this.smsSender = smsSender;
    }

    @Override
    public void afterPropertiesSet() {
        validateJwt();
        validateStorage();
        validateAdapterAvailability();
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            validateProduction();
        }
        String[] profiles = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles()
                : environment.getDefaultProfiles();
        log.info("配置校验通过: profile={}, storage={}, aiProvider={}, smsProvider={}",
                String.join(",", profiles),
                storage.type(), integration.ai().provider(), integration.sms().provider());
    }

    /**
     * 适配器开关与 Bean 必须一致：声明非 mock 却没有实现时直接启动失败，
     * 避免运行期才发现外部能力缺失。
     */
    private void validateAdapterAvailability() {
        if (!integration.ai().isMock()) {
            require(llmProvider.getIfAvailable() != null,
                    "integration.ai.provider=" + integration.ai().provider() + " 但未找到 LlmProvider 实现");
            require(asrProvider.getIfAvailable() != null,
                    "integration.ai.provider=" + integration.ai().provider() + " 但未找到 AsrProvider 实现");
            require(ttsProvider.getIfAvailable() != null,
                    "integration.ai.provider=" + integration.ai().provider() + " 但未找到 TtsProvider 实现");
        }
        if (!integration.sms().isMock()) {
            require(smsSender.getIfAvailable() != null,
                    "integration.sms.provider=" + integration.sms().provider() + " 但未找到 SmsSender 实现");
        }
    }

    private void validateJwt() {
        if (app.security().c().secret().equals(app.security().admin().secret())) {
            throw new IllegalStateException("C/B 两端 JWT 密钥必须不同");
        }
    }

    private void validateStorage() {
        String type = storage.type() == null ? "" : storage.type().toLowerCase();
        switch (type) {
            case "s3" -> {
                StorageProperties.S3 s3 = storage.s3();
                require(s3 != null, "storage.s3 配置缺失");
                requireText(s3.endpoint(), "storage.s3.endpoint");
                requireText(s3.publicEndpoint(), "storage.s3.public-endpoint");
                requireText(s3.accessKey(), "storage.s3.access-key");
                requireText(s3.secretKey(), "storage.s3.secret-key");
                requireText(s3.bucket(), "storage.s3.bucket");
                requireText(s3.region(), "storage.s3.region");
                if (!s3.pathStyle()) {
                    throw new IllegalStateException("MinIO 场景要求 storage.s3.path-style=true，"
                            + "否则签名 URL 的 host 不可达（06 文档 4.1）");
                }
            }
            case "oss" -> {
                StorageProperties.Oss oss = storage.oss();
                require(oss != null, "storage.oss 配置缺失");
                requireText(oss.endpoint(), "storage.oss.endpoint");
                requireText(oss.publicEndpoint(), "storage.oss.public-endpoint");
                requireText(oss.accessKey(), "storage.oss.access-key");
                requireText(oss.secretKey(), "storage.oss.secret-key");
                requireText(oss.bucket(), "storage.oss.bucket");
                requireText(oss.region(), "storage.oss.region");
            }
            case "local" -> throw new IllegalStateException(
                    "storage.type=local 仅允许用于单元测试，开发用 s3（MinIO），生产用 oss");
            default -> throw new IllegalStateException("storage.type 只能是 local|s3|oss，当前=" + storage.type());
        }
    }

    private void validateProduction() {
        if (!storage.isOss()) {
            throw new IllegalStateException("生产环境 storage.type 只允许 oss");
        }
        if (integration.ai().isMock()) {
            throw new IllegalStateException("生产环境 AI 不允许使用 mock 适配器");
        }
        if (integration.sms().isMock()) {
            throw new IllegalStateException("生产环境短信不允许使用 mock 适配器");
        }
        if (app.cors().originList().contains("*")) {
            throw new IllegalStateException("生产环境 CORS 不允许通配 *");
        }
        if (app.security().c().secret().contains("dev-only")
                || app.security().admin().secret().contains("dev-only")) {
            throw new IllegalStateException("生产环境必须通过环境变量注入 JWT 密钥，禁止使用开发默认值");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少必填配置: " + name);
        }
    }
}
