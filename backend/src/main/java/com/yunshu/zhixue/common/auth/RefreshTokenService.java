package com.yunshu.zhixue.common.auth;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.config.AppProperties;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.web.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Refresh Token 哈希落库、轮换与撤销（02 §2.6 / api-rules §8.10）。
 *
 * <p>原始 token 只在 Set-Cookie 中出现一次，库中仅存 SHA-256 哈希；
 * 轮换以条件更新（revoked_at IS NULL）做 CAS 防并发重放；
 * 已轮换 token 的重用视为泄露，撤销该主体全部 refresh token。</p>
 *
 * <p>所有时间均为 UTC（与 V1 DATETIME(3) 约定一致）。</p>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final int SUBJECT_STUDENT = 1;
    private static final int SUBJECT_ADMIN = 2;

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public RefreshTokenService(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** 签发记录：raw 只在本次响应/Set-Cookie 中使用一次 */
    public record IssuedRefresh(long id, int subjectType, long subjectId, String token, LocalDateTime expiresAt) {
    }

    /** 签发新 refresh token */
    @Transactional
    public IssuedRefresh issue(int subjectType, long subjectId) {
        String raw = newToken();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plus(properties.security().refreshTtl());
        jdbcTemplate.update(
                "INSERT INTO refresh_token (subject_type, subject_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                subjectType, subjectId, hashHex(raw), expiresAt);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM refresh_token WHERE token_hash = ?", Long.class, hashHex(raw));
        return new IssuedRefresh(id == null ? 0L : id, subjectType, subjectId, raw, expiresAt);
    }

    /**
     * 轮换：校验旧 token → CAS 撤销 → 签发新 token 并回填 replaced_by_id。
     * 失败抛 20008/20009/20007；不延长绝对过期时间（api-rules §8.10）。
     */
    @Transactional
    public IssuedRefresh rotate(String rawToken, Audience audience) {
        String hash = hashHex(rawToken);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int expectedSubjectType = audience == Audience.C ? SUBJECT_STUDENT : SUBJECT_ADMIN;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, subject_type, subject_id, expires_at, revoked_at, replaced_by_id "
                        + "FROM refresh_token WHERE token_hash = ?", hash);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        int subjectType = ((Number) row.get("subject_type")).intValue();
        long subjectId = ((Number) row.get("subject_id")).longValue();
        LocalDateTime expiresAt = (LocalDateTime) row.get("expires_at");
        boolean revoked = row.get("revoked_at") != null;
        boolean replaced = row.get("replaced_by_id") != null;

        if (subjectType != expectedSubjectType) {
            throw new BizException(ErrorCode.TOKEN_AUDIENCE_MISMATCH);
        }
        if (replaced) {
            log.warn("Refresh token 重用（轮换后重放）subjectId={} traceId={}", subjectId, TraceIdFilter.current());
            revokeAllFor(subjectType, subjectId, 6);
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (revoked) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (expiresAt.isBefore(now)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        int updated = jdbcTemplate.update(
                "UPDATE refresh_token SET revoked_at = ?, revoke_reason = 5 WHERE id = ? AND revoked_at IS NULL",
                now, id);
        if (updated == 0) {
            log.warn("Refresh token 并发重用 subjectId={} traceId={}", subjectId, TraceIdFilter.current());
            revokeAllFor(subjectType, subjectId, 6);
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        IssuedRefresh issued = issue(subjectType, subjectId);
        jdbcTemplate.update("UPDATE refresh_token SET replaced_by_id = ? WHERE id = ?", issued.id(), id);
        return issued;
    }

    /** 登出：撤销该条 refresh（幂等；不存在/已撤销时静默返回） */
    @Transactional
    public void revoke(String rawToken) {
        String hash = hashHex(rawToken);
        jdbcTemplate.update(
                "UPDATE refresh_token SET revoked_at = ?, revoke_reason = 1 WHERE token_hash = ? AND revoked_at IS NULL",
                LocalDateTime.now(ZoneOffset.UTC), hash);
    }

    /** 撤销该主体全部 refresh token（改密/禁用/注销/泄露） */
    @Transactional
    public void revokeAllFor(int subjectType, long subjectId, int reason) {
        jdbcTemplate.update(
                "UPDATE refresh_token SET revoked_at = ?, revoke_reason = ? "
                        + "WHERE subject_type = ? AND subject_id = ? AND revoked_at IS NULL",
                LocalDateTime.now(ZoneOffset.UTC), reason, subjectType, subjectId);
    }

    /** SHA-256 hex（token_hash CHAR(64)） */
    private static String hashHex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /** 32 字节随机 → Base64URL（43 字符） */
    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
