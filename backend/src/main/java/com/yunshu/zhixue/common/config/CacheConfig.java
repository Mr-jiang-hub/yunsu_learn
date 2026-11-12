package com.yunshu.zhixue.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * P0 不引入 Redis，使用 Caffeine 本地短 TTL 缓存（02 §2.4）。
 * 多实例部署前必须迁移到共享缓存，否则数据不一致。
 */
@EnableCaching
@Configuration
public class CacheConfig {

    public static final String ACCOUNT_STATUS_CACHE = "accountStatus";
    public static final String MODULE_PERMISSION_CACHE = "modulePermission";
    public static final String DASHBOARD_CACHE = "dashboard";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(ACCOUNT_STATUS_CACHE, MODULE_PERMISSION_CACHE, DASHBOARD_CACHE));
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats());
        return manager;
    }
}
