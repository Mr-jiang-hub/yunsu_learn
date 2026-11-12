package com.yunshu.zhixue.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 应用级配置：C/B 双端 JWT、CORS、功能开关、异步线程池。
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Security security,
        @NotNull Cors cors,
        @NotNull Features features,
        @NotNull Async async
) {

    public record Security(
            @Valid @NotNull Chain c,
            @Valid @NotNull Chain admin,
            @NotNull Duration refreshTtl,
            /** 手机号 AES-GCM 密钥，Base64 解码后必须为 32 字节（02-Q18） */
            @NotBlank String phoneAesKey,
            /** 手机号 HMAC-SHA256 盲索引密钥，Base64 解码后必须为 32 字节 */
            @NotBlank String phoneHmacKey
    ) {
        public Chain chain(String audience) {
            return "admin".equals(audience) ? admin : c;
        }
    }

    public record Chain(
            @NotBlank @Size(min = 32, message = "JWT 密钥长度不得少于 32 字节") String secret,
            @NotNull Duration accessTtl
    ) {
    }

    public record Cors(@NotBlank String allowedOrigins) {
        public List<String> originList() {
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }

    public record Features(boolean aiEnabled, boolean ttsEnabled) {
    }

    public record Async(
            @Min(1) int corePoolSize,
            @Min(1) int maxPoolSize,
            @Min(1) int queueCapacity
    ) {
    }
}
