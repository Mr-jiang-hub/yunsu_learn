package com.yunshu.zhixue.common.idempotency;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * `Idempotency-Key` 结果持久化（api-rules §9.2 / 05 G-04）。
 *
 * <p>要求：带幂等键的写请求，重放必须返回首次结果，且结果至少保存 24 小时。
 * 迁移前只有"60 秒内复用进行中会话"兜底，撑不住 24 小时，因此新增
 * `idempotency_record` 表承载。</p>
 *
 * <p>两个边界：
 * <ul>
 *   <li>同 key 但请求体不同 → 40042（客户端复用错了 key，不静默返回旧结果）；</li>
 *   <li>首次请求失败 → 不缓存响应，只留一条 status=2 的记录占位，
 *       让客户端可以用同一个 key 重试（失败不该被幂等锁死）。</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    /** 结果保留时长，与 api-rules §9.2 一致 */
    private static final Duration TTL = Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查找已完成的幂等结果。
     *
     * @return 首次成功的响应快照；没有记录或记录已过期返回 null
     */
    public String findResponse(String scope, String key, String canonicalRequest, long subjectId) {
        cleanExpired();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT request_hash, response_json, status FROM idempotency_record "
                        + "WHERE scope = ? AND idempotency_key = ? AND expire_at > ?",
                scope, key, LocalDateTime.now(ZoneOffset.UTC));
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String storedHash = String.valueOf(row.get("request_hash"));
        String requestHash = sha256(canonicalRequest);
        if (!storedHash.equals(requestHash)) {
            // 同一个 key 换了请求体：不返回旧结果，让调用方换 key 重来
            throw new BizException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "该幂等键已用于其他请求");
        }
        Object response = row.get("response_json");
        return response == null ? null : String.valueOf(response);
    }

    /** 写入成功结果；同 key 已存在时覆盖（用于首次失败后重试成功的场景） */
    public void save(String scope, String key, String canonicalRequest, long subjectId, String responseJson) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO idempotency_record "
                        + "(scope, idempotency_key, subject_id, request_hash, response_json, status, expire_at) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?) "
                        + "ON DUPLICATE KEY UPDATE request_hash = VALUES(request_hash), "
                        + "response_json = VALUES(response_json), status = 1, expire_at = VALUES(expire_at)",
                scope, key, subjectId, sha256(canonicalRequest), responseJson, now.plus(TTL));
    }

    /**
     * 首个请求失败时占位：不写响应，让同一 key 可以重试。
     * 过期时间与成功记录一致，避免长期占用。
     */
    public void markFailure(String scope, String key, String canonicalRequest, long subjectId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO idempotency_record "
                        + "(scope, idempotency_key, subject_id, request_hash, response_json, status, expire_at) "
                        + "VALUES (?, ?, ?, ?, NULL, 2, ?) "
                        + "ON DUPLICATE KEY UPDATE status = 2, expire_at = VALUES(expire_at)",
                scope, key, subjectId, sha256(canonicalRequest), now.plus(TTL));
    }

    /** 顺手清理过期记录，避免表无限增长；调用频率低，不做独立定时任务 */
    private void cleanExpired() {
        jdbcTemplate.update("DELETE FROM idempotency_record WHERE expire_at <= ?",
                LocalDateTime.now(ZoneOffset.UTC));
    }

    /** 规范化请求体的 SHA-256：字段顺序无关由调用方保证，这里只做摘要 */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /** 便于日志与文档核对的 TTL 描述 */
    public static String ttlDescription() {
        return TimeFormats.rfc3339(LocalDateTime.now(ZoneOffset.UTC).plus(TTL));
    }
}
