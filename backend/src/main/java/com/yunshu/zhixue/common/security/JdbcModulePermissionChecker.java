package com.yunshu.zhixue.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 模块权限查库 + ≤ 60 秒本地缓存。权限变更后最长 60 秒生效（04 文档 4.5）。
 */
@Component
public class JdbcModulePermissionChecker implements ModulePermissionChecker {

    private record CacheKey(long adminUserId, String moduleCode) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Cache<CacheKey, Boolean> permissionCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public JdbcModulePermissionChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasModule(long adminUserId, String moduleCode) {
        return Boolean.TRUE.equals(permissionCache.get(new CacheKey(adminUserId, moduleCode), this::load));
    }

    public void evictAll(long adminUserId) {
        permissionCache.asMap().keySet().removeIf(key -> key.adminUserId() == adminUserId);
    }

    private Boolean load(CacheKey key) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM admin_user au
                  JOIN admin_role_module arm ON arm.role_id = au.role_id
                 WHERE au.id = ?
                   AND au.status = 1
                   AND arm.module_code = ?
                """, Integer.class, key.adminUserId(), key.moduleCode());
        return count != null && count > 0;
    }
}
