package com.yunshu.zhixue.common.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 登录失败计数（login_failure 表，重启不丢）。
 * C 端：连续失败达阈值后强制验证码登录（30007），不设时间锁；
 * B 端：连续 5 次锁 15 分钟（20011）。成功登录后调用 reset 清零。
 */
@Service
public class LoginFailureService {

    public static final int ACCOUNT_TYPE_STUDENT = 1;
    public static final int ACCOUNT_TYPE_ADMIN = 2;

    private final JdbcTemplate jdbcTemplate;

    public LoginFailureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int failureCount(int accountType, String accountKey) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT failure_count FROM login_failure WHERE account_type = ? AND account_key = ?",
                Integer.class, accountType, accountKey);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    /** B 端锁定检查：locked_until 未到 → 20011 */
    public void checkNotLocked(int accountType, String accountKey) {
        List<LocalDateTime> rows = jdbcTemplate.queryForList(
                "SELECT locked_until FROM login_failure WHERE account_type = ? AND account_key = ?",
                LocalDateTime.class, accountType, accountKey);
        if (!rows.isEmpty() && rows.get(0) != null
                && rows.get(0).isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new com.yunshu.zhixue.common.exception.BizException(
                    com.yunshu.zhixue.common.api.ErrorCode.LOGIN_LOCKED);
        }
    }

    /** 记一次失败并返回最新计数；lockAtThreshold 时达到阈值写入 locked_until */
    @Transactional
    public int recordFailure(int accountType, String accountKey, boolean lockAtThreshold,
                             int threshold, Duration lockDuration) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = jdbcTemplate.update(
                "UPDATE login_failure SET failure_count = failure_count + 1, last_failed_at = ? "
                        + "WHERE account_type = ? AND account_key = ?",
                now, accountType, accountKey);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO login_failure (account_type, account_key, failure_count, first_failed_at, last_failed_at) "
                            + "VALUES (?, ?, 1, ?, ?)",
                    accountType, accountKey, now, now);
        }
        int count = Math.max(1, failureCount(accountType, accountKey));
        if (lockAtThreshold && count >= threshold) {
            jdbcTemplate.update(
                    "UPDATE login_failure SET locked_until = ? WHERE account_type = ? AND account_key = ?",
                    now.plus(lockDuration), accountType, accountKey);
        }
        return count;
    }

    /** 成功登录/验证码通过后清零（删行，同时解除锁定） */
    @Transactional
    public void reset(int accountType, String accountKey) {
        jdbcTemplate.update(
                "DELETE FROM login_failure WHERE account_type = ? AND account_key = ?",
                accountType, accountKey);
    }
}
