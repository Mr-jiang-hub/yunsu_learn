package com.yunshu.zhixue.student.auth;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.crypto.PhoneCrypto;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.MaskUtils;
import com.yunshu.zhixue.common.web.TraceIdFilter;
import com.yunshu.zhixue.integration.sms.SmsSender;
import com.yunshu.zhixue.student.auth.dto.SendSmsCodeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 短信验证码：发送频控与一次性校验（04 §6.1.1 / 02 §6.1）。
 * 频控走 sms_send_quota 行级原子条件更新（60 秒 1 次、自然日 10 次），
 * 不依赖 Redis；验证码只存 HMAC 哈希，校验用条件更新保证一次性。
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int CODE_EXPIRE_SECONDS = 300;
    private static final int RESEND_INTERVAL_SECONDS = 60;
    private static final int DAILY_LIMIT = 10;
    private static final String CODE_PATTERN = "^\\d{6}$";

    private final JdbcTemplate jdbcTemplate;
    private final PhoneCrypto phoneCrypto;
    private final SmsSender smsSender;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(JdbcTemplate jdbcTemplate, PhoneCrypto phoneCrypto, SmsSender smsSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.phoneCrypto = phoneCrypto;
        this.smsSender = smsSender;
    }

    /**
     * 发送验证码。先原子占频控额度再调网关；30003/30004 之外不占用额度。
     */
    @Transactional
    public SendSmsCodeResponse send(String phone, SmsPurpose purpose, String requestIp) {
        String phoneHash = phoneCrypto.hmacHex(phone);
        LocalDate businessDate = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        jdbcTemplate.update(
                "INSERT IGNORE INTO sms_send_quota (phone_hash, send_date) VALUES (?, ?)",
                phoneHash, businessDate);
        int claimed = jdbcTemplate.update(
                "UPDATE sms_send_quota SET send_count = send_count + 1, last_sent_at = ? "
                        + "WHERE phone_hash = ? AND send_date = ? "
                        + "AND (last_sent_at IS NULL OR last_sent_at < ?) AND send_count < ?",
                now, phoneHash, businessDate, now.minusSeconds(RESEND_INTERVAL_SECONDS), DAILY_LIMIT);
        if (claimed == 0) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT send_count, last_sent_at FROM sms_send_quota WHERE phone_hash = ? AND send_date = ?",
                    phoneHash, businessDate);
            if (rows.isEmpty()) {
                throw new BizException(ErrorCode.SMS_SEND_TOO_FREQUENT);
            }
            LocalDateTime lastSentAt = (LocalDateTime) rows.get(0).get("last_sent_at");
            int sendCount = ((Number) rows.get(0).get("send_count")).intValue();
            if (lastSentAt != null && lastSentAt.isAfter(now.minusSeconds(RESEND_INTERVAL_SECONDS))) {
                long retryAfter = RESEND_INTERVAL_SECONDS - Duration.between(lastSentAt, now).getSeconds();
                throw new BizException(ErrorCode.SMS_SEND_TOO_FREQUENT, ErrorCode.SMS_SEND_TOO_FREQUENT.getMessage(),
                        Map.of("retryAfterSeconds", Math.max(1, retryAfter)));
            }
            throw new BizException(ErrorCode.SMS_DAILY_LIMIT, ErrorCode.SMS_DAILY_LIMIT.getMessage(),
                    Map.of("remainingToday", 0));
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        // 重发作废旧验证码（status=4 已作废），保证同号同用途只有一个未用验证码
        jdbcTemplate.update(
                "UPDATE sms_code SET status = 4 WHERE phone_hash = ? AND purpose = ? AND status = 1",
                phoneHash, purpose.dbValue());
        try {
            smsSender.sendVerificationCode(phone, code, purpose.scene());
        } catch (Exception ex) {
            log.error("短信网关调用失败 traceId={}", TraceIdFilter.current(), ex);
            throw new BizException(ErrorCode.SMS_GATEWAY_FAILED);
        }
        jdbcTemplate.update(
                "INSERT INTO sms_code (phone_hash, purpose, code_hash, expire_at, request_ip) VALUES (?, ?, ?, ?, ?)",
                phoneHash, purpose.dbValue(), phoneCrypto.hmacHex(code),
                now.plusSeconds(CODE_EXPIRE_SECONDS), requestIp == null ? "" : requestIp);
        int sendCount = jdbcTemplate.queryForObject(
                "SELECT send_count FROM sms_send_quota WHERE phone_hash = ? AND send_date = ?",
                Integer.class, phoneHash, businessDate);
        log.info("[mock 短信已记录] phoneMasked={} purpose={}",
                MaskUtils.maskPhone(phone), purpose);
        return new SendSmsCodeResponse(
                true, CODE_EXPIRE_SECONDS, RESEND_INTERVAL_SECONDS, DAILY_LIMIT - sendCount);
    }

    /**
     * 一次性校验：命中即置已用（status=2）。未命中统一返回 30002，不区分错码/过期/已用。
     */
    @Transactional
    public void verify(String phone, SmsPurpose purpose, String code) {
        if (code == null || !code.matches(CODE_PATTERN)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        String phoneHash = phoneCrypto.hmacHex(phone);
        int consumed = jdbcTemplate.update(
                "UPDATE sms_code SET status = 2, used_at = ? "
                        + "WHERE phone_hash = ? AND purpose = ? AND code_hash = ? AND status = 1 AND expire_at > ?",
                LocalDateTime.now(ZoneOffset.UTC), phoneHash, purpose.dbValue(),
                phoneCrypto.hmacHex(code), LocalDateTime.now(ZoneOffset.UTC));
        if (consumed == 0) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
    }
}
