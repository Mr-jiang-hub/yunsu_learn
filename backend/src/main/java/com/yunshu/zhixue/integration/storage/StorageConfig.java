package com.yunshu.zhixue.integration.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 storage.type 装配唯一实现；不允许同时装配两个真实实现。
 * local 仅用于单元测试，不在此装配（06 文档第 3 章）。
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "s3")
    public ObjectStorage s3StorageAdapter(StorageProperties properties) {
        return new S3StorageAdapter(properties.s3());
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "oss")
    public ObjectStorage ossStorageAdapter(StorageProperties properties) {
        return new OssStorageAdapter(properties.oss());
    }
}
