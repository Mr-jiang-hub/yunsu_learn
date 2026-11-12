package com.yunshu.zhixue.student.profile;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.auth.RefreshTokenService;
import com.yunshu.zhixue.common.crypto.PhoneCrypto;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.JdbcAccountStatusVerifier;
import com.yunshu.zhixue.common.storage.CoverUrlResolver;
import com.yunshu.zhixue.common.util.MaskUtils;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.auth.SmsCodeService;
import com.yunshu.zhixue.student.auth.SmsPurpose;
import com.yunshu.zhixue.student.auth.dto.SendSmsCodeResponse;
import com.yunshu.zhixue.student.profile.dto.CancelAccountRequest;
import com.yunshu.zhixue.student.profile.dto.ChangePasswordRequest;
import com.yunshu.zhixue.student.profile.dto.PhoneChangeRequest;
import com.yunshu.zhixue.student.profile.dto.PhoneChangeResponse;
import com.yunshu.zhixue.student.profile.dto.StudentProfileResponse;
import com.yunshu.zhixue.student.profile.dto.UpdateProfileRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * C 端个人资料（04 §6 第 37 号接口 + §6.9）。
 * 手机号只以密文落库，展示时解密后掩码，绝不返回原文。
 *
 * <p>改密 / 换绑 / 注销都会撤销该学员的全部 refresh token：
 * access token 依赖每请求状态校验与 60 秒缓存，最迟 60 秒失效（api-rules §10）。</p>
 */
@Service
public class StudentProfileService {

    private static final DateTimeFormatter RFC3339_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$");
    private static final Pattern AVATAR_KEY_RULE = Pattern.compile("^avatar/[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$");

    private static final int STATUS_CANCELLED = 3;
    private static final int SUBJECT_TYPE_STUDENT = 1;
    private static final int REVOKE_PASSWORD_CHANGED = 2;
    private static final int REVOKE_CANCELLED = 4;
    private static final String CANCEL_CONFIRM_TEXT = "注销";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final JdbcTemplate jdbcTemplate;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SmsCodeService smsCodeService;
    private final CoverUrlResolver coverUrlResolver;
    private final JdbcAccountStatusVerifier accountStatusVerifier;

    public StudentProfileService(JdbcTemplate jdbcTemplate,
                                 PhoneCrypto phoneCrypto,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService,
                                 SmsCodeService smsCodeService,
                                 CoverUrlResolver coverUrlResolver,
                                 JdbcAccountStatusVerifier accountStatusVerifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.phoneCrypto = phoneCrypto;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.smsCodeService = smsCodeService;
        this.coverUrlResolver = coverUrlResolver;
        this.accountStatusVerifier = accountStatusVerifier;
    }

    /** 只认 token 里的 studentId；查不到即视为登录态失效（20001） */
    @Transactional(readOnly = true)
    public StudentProfileResponse profile(long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id, s.nickname, s.phone_cipher, s.avatar_url, s.status, s.current_path_id, "
                        + "s.password_hash, s.created_at, p.name AS path_name "
                        + "FROM student s LEFT JOIN learning_path p ON p.id = s.current_path_id "
                        + "WHERE s.id = ?",
                studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        Map<String, Object> row = rows.get(0);

        Long pathId = row.get("current_path_id") == null
                ? null : ((Number) row.get("current_path_id")).longValue();
        StudentProfileResponse.CurrentPath currentPath =
                pathId == null ? null : new StudentProfileResponse.CurrentPath(pathId, (String) row.get("path_name"));

        String passwordHash = (String) row.get("password_hash");
        String avatarKey = (String) row.get("avatar_url");

        return new StudentProfileResponse(
                studentId,
                (String) row.get("nickname"),
                maskedPhone((byte[]) row.get("phone_cipher")),
                coverUrlResolver.resolve(avatarKey),
                ((Number) row.get("status")).intValue(),
                currentPath,
                passwordHash != null && !passwordHash.isBlank(),
                formatCreatedAt(row.get("created_at")));
    }

    // ------------------------------------------------------------------ 更新资料

    /** 04 §6.9 PATCH /c/students/me：只允许昵称与头像，且至少提供一个字段 */
    @Transactional
    public StudentProfileResponse update(long studentId, UpdateProfileRequest request) {
        if (request == null || request.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少提供一个可修改字段");
        }
        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            if (nickname.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "昵称不能为空");
            }
            jdbcTemplate.update("UPDATE student SET nickname = ? WHERE id = ?", nickname, studentId);
        }
        if (request.avatarKey() != null) {
            String key = request.avatarKey().trim();
            if (!AVATAR_KEY_RULE.matcher(key).matches() || key.contains("..")) {
                throw new BizException(ErrorCode.PARAM_INVALID, "头像 key 不合法，请先上传图片");
            }
            jdbcTemplate.update("UPDATE student SET avatar_url = ? WHERE id = ?", key, studentId);
        }
        return profile(studentId);
    }

    // ------------------------------------------------------------------ 修改密码

    /**
     * 修改密码：原密码或验证码**二选一**（04 §6.9 / PRD 5.10 / 评审缺口 G-02）。
     * 旧密码错误 30008，策略不符 30005，验证码无效 30002。
     *
     * <p>两个凭证字段都可选，"恰好传一个"由这里校验——Bean Validation 表达不了互斥，
     * 所以 DTO 上没有 {@code @NotBlank}。都传或都不传一律 10001：
     * 不静默偏向某一种，避免客户端靠"多传一个"绕过校验。</p>
     */
    @Transactional
    public void changePassword(long studentId, ChangePasswordRequest request) {
        boolean byOldPassword = request.oldPassword() != null && !request.oldPassword().isBlank();
        boolean bySmsCode = request.smsCode() != null && !request.smsCode().isBlank();
        if (byOldPassword == bySmsCode) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原密码与验证码需二选一");
        }

        Map<String, Object> student = requireCredential(studentId);
        String passwordHash = (String) student.get("password_hash");
        if (byOldPassword) {
            if (passwordHash == null || !passwordEncoder.matches(request.oldPassword(), passwordHash)) {
                throw new BizException(ErrorCode.PASSWORD_OLD_MISMATCH);
            }
        } else {
            String currentPhone = plainPhone((byte[]) student.get("phone_cipher"));
            if (currentPhone == null) {
                throw new BizException(ErrorCode.UNAUTHENTICATED);
            }
            // 验证码校验会直接核销该码，失败抛 30002；通过后下面仍要校验新密码策略
            smsCodeService.verify(currentPhone, SmsPurpose.CHANGE_PASSWORD, request.smsCode());
        }

        if (!PASSWORD_RULE.matcher(request.newPassword()).matches()) {
            throw new BizException(ErrorCode.PASSWORD_POLICY);
        }
        if (passwordHash != null && passwordEncoder.matches(request.newPassword(), passwordHash)) {
            throw new BizException(ErrorCode.PASSWORD_POLICY, "新密码不能与原密码相同");
        }
        jdbcTemplate.update("UPDATE student SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(request.newPassword()), studentId);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, studentId, REVOKE_PASSWORD_CHANGED);
    }

    // ------------------------------------------------------------------ 换绑手机号

    /**
     * 给本人已绑定手机号发验证码：换绑时要验原号、注销时要验本人号，
     * 前端只有掩码手机号，因此由服务端按 token 取号下发，不走客户端回填。
     * 只允许 CHANGE_PHONE_OLD / CANCEL 两种用途。
     *
     * <p>不加 {@code readOnly}：发码要写 sms_send_quota / sms_code，只读连接会直接报错；
     * 事务边界由 {@link SmsCodeService#send} 自己把控。</p>
     */
    public SendSmsCodeResponse sendSelfCode(long studentId, String purpose) {
        SmsPurpose smsPurpose = SmsPurpose.valueOf(purpose);
        if (smsPurpose != SmsPurpose.CHANGE_PHONE_OLD
                && smsPurpose != SmsPurpose.CANCEL
                && smsPurpose != SmsPurpose.CHANGE_PASSWORD) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用途不支持");
        }
        Map<String, Object> student = requireCredential(studentId);
        String currentPhone = plainPhone((byte[]) student.get("phone_cipher"));
        if (currentPhone == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return smsCodeService.send(currentPhone, smsPurpose, "");
    }

    /** 04 §6.9 POST /c/students/me/phone-change：原号与新号双重验证码 */
    @Transactional
    public PhoneChangeResponse changePhone(long studentId, PhoneChangeRequest request) {
        Map<String, Object> student = requireCredential(studentId);
        String currentPhone = plainPhone((byte[]) student.get("phone_cipher"));
        if (currentPhone == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (currentPhone.equals(request.newPhone())) {
            throw new BizException(ErrorCode.PHONE_UNCHANGED);
        }
        smsCodeService.verify(currentPhone, SmsPurpose.CHANGE_PHONE_OLD, request.oldCode());

        String newHash = phoneCrypto.hmacHex(request.newPhone());
        Long taken = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student WHERE phone_hash = ? AND id <> ?", Long.class, newHash, studentId);
        if (taken != null && taken > 0) {
            throw new BizException(ErrorCode.PHONE_ALREADY_TAKEN);
        }
        smsCodeService.verify(request.newPhone(), SmsPurpose.CHANGE_PHONE_NEW, request.newCode());

        jdbcTemplate.update("UPDATE student SET phone_cipher = ?, phone_hash = ? WHERE id = ?",
                phoneCrypto.encrypt(request.newPhone()), newHash, studentId);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, studentId, REVOKE_PASSWORD_CHANGED);
        return new PhoneChangeResponse(MaskUtils.maskPhone(request.newPhone()),
                TimeFormats.rfc3339(Instant.now()));
    }

    // ------------------------------------------------------------------ 注销账号

    /** 04 §6.9 POST /c/students/me/cancel：二次确认文本 + 验证码；注销后手机号释放、历史脱敏 */
    @Transactional
    public void cancel(long studentId, CancelAccountRequest request) {
        if (!CANCEL_CONFIRM_TEXT.equals(request.confirmText() == null ? null : request.confirmText().trim())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写确认文本「注销」");
        }
        Map<String, Object> student = requireCredential(studentId);
        if (intValue(student.get("status")) == STATUS_CANCELLED) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLED);
        }
        String currentPhone = plainPhone((byte[]) student.get("phone_cipher"));
        if (currentPhone == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            smsCodeService.verify(currentPhone, SmsPurpose.CANCEL, request.code());
        } catch (BizException ex) {
            // 注销场景前端要提示"注销验证码错误"，与登录/注册的 30002 区分
            throw new BizException(ErrorCode.CANCEL_CODE_INVALID);
        }

        LocalDateTime cancelledAt = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "UPDATE student SET status = ?, cancelled_at = ?, phone_cipher = NULL, phone_hash = NULL, "
                        + "nickname = NULL, avatar_url = NULL, password_hash = ? WHERE id = ?",
                STATUS_CANCELLED, cancelledAt, passwordEncoder.encode(randomPassword()), studentId);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, studentId, REVOKE_CANCELLED);
        // 注销后立即生效，不等 60 秒状态缓存过期（02 §6.1）
        accountStatusVerifier.evict(studentId, Audience.C);
    }

    // ------------------------------------------------------------------ 内部

    private Map<String, Object> requireCredential(long studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status, phone_cipher, password_hash FROM student WHERE id = ?", studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return rows.get(0);
    }

    private String plainPhone(byte[] cipher) {
        if (cipher == null || cipher.length == 0) {
            return null;
        }
        return new String(phoneCrypto.decrypt(cipher), StandardCharsets.UTF_8);
    }

    /** 注销后写入随机密码，确保原密码不可再登录 */
    private static String randomPassword() {
        StringBuilder builder = new StringBuilder(24);
        for (int i = 0; i < 20; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        builder.append(RANDOM.nextInt(10));
        return builder.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return 0;
    }

    private String maskedPhone(byte[] cipher) {
        if (cipher == null || cipher.length == 0) {
            return null;
        }
        return MaskUtils.maskPhone(new String(phoneCrypto.decrypt(cipher), StandardCharsets.UTF_8));
    }

    private static String formatCreatedAt(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return RFC3339_UTC.format(dateTime.toInstant(ZoneOffset.UTC));
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return RFC3339_UTC.format(timestamp.toInstant());
        }
        return null;
    }
}
