package com.yunshu.zhixue.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 直接查库校验账号状态，使用 ≤ 60 秒本地缓存（无 Redis，02 §6.1）。
 * 停用/注销/改密后最长 60 秒内生效；涉及敏感操作时业务层应主动 evict。
 */
@Component
public class JdbcAccountStatusVerifier implements AccountStatusVerifier {

    private record CacheKey(long id, Audience audience) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Cache<CacheKey, Integer> statusCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public JdbcAccountStatusVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void verify(AuthPrincipal principal) {
        int status = statusCache.get(new CacheKey(principal.id(), principal.audience()), this::loadStatus);
        switch (principal.audience()) {
            case C -> {
                if (status == 2) {
                    throw new BizException(ErrorCode.ACCOUNT_DISABLED);
                }
                if (status == 3) {
                    throw new BizException(ErrorCode.ACCOUNT_CANCELLED);
                }
            }
            case ADMIN -> {
                if (status == 2) {
                    throw new BizException(ErrorCode.ACCOUNT_DISABLED);
                }
            }
        }
    }

    public void evict(long id, Audience audience) {
        statusCache.invalidate(new CacheKey(id, audience));
    }

    private int loadStatus(CacheKey key) {
        List<Integer> rows = switch (key.audience()) {
            case C -> jdbcTemplate.queryForList(
                    "SELECT status FROM student WHERE id = ?", Integer.class, key.id());
            case ADMIN -> jdbcTemplate.queryForList(
                    "SELECT status FROM admin_user WHERE id = ?", Integer.class, key.id());
        };
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
        return rows.get(0);
    }
}
