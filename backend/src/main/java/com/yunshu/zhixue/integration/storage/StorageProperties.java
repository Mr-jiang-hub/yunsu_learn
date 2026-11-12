package com.yunshu.zhixue.integration.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置，结构见 docs/design/06.对象存储端口设计.md 第 6 章。
 *
 * <p>S3 与 OSS 是两套独立适配器：不是改 endpoint 复用同一个实现。</p>
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String type, S3 s3, Oss oss) {

    public boolean isS3() {
        return "s3".equalsIgnoreCase(type);
    }

    public boolean isOss() {
        return "oss".equalsIgnoreCase(type);
    }

    public record S3(
            String endpoint,
            String publicEndpoint,
            String accessKey,
            String secretKey,
            String bucket,
            String region,
            boolean pathStyle
    ) {
    }

    public record Oss(
            String endpoint,
            String publicEndpoint,
            String accessKey,
            String secretKey,
            String bucket,
            String region
    ) {
    }
}
