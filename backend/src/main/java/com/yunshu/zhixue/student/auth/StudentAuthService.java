package com.yunshu.zhixue.student.auth;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.auth.AuthCookieService;
import com.yunshu.zhixue.common.auth.LoginFailureService;
import com.yunshu.zhixue.common.auth.RefreshTokenService;
import com.yunshu.zhixue.common.crypto.PhoneCrypto;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.JwtTokenProvider;
import com.yunshu.zhixue.common.util.MaskUtils;
import com.yunshu.zhixue.student.auth.dto.AuthSessionResponse;
import com.yunshu.zhixue.student.auth.dto.StudentSummary;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * C 端认证业务（04 §6.1）：注册、密码/验证码登录、重置密码。
 * 手机号只以 AES 密文 + HMAC 盲索引落库；登录失败计数走 login_failure。
 */
@Service
public class StudentAuthService {

    private static final DateTimeFormatter RFC3339_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$");
    private static final int C_PASSWORD_FAILURE_THRESHOLD = 5;
    private static final int SUBJECT_TYPE_STUDENT = 1;
    private static final int STATUS_DISABLED = 2;
    private static final int STATUS_CANCELLED = 3;

    private final JdbcTemplate jdbcTemplate;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService cookieService;
    private final LoginFailureService loginFailureService;

    public StudentAuthService(JdbcTemplate jdbcTemplate, PhoneCrypto phoneCrypto,
                              PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
                              RefreshTokenService refreshTokenService, AuthCookieService cookieService,
                              LoginFailureService loginFailureService) {
        this.jdbcTemplate = jdbcTemplate;
        this.phoneCrypto = phoneCrypto;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.cookieService = cookieService;
        this.loginFailureService = loginFailureService;
    }

    /** 注册：验证码已在控制器消费；昵称缺省“学员+手机号后四位” */
    @Transactional
    public AuthSessionResponse register(String phone, String password, String nickname,
                                        HttpServletResponse response) {
        requirePasswordPolicy(password);
        String phoneHash = phoneCrypto.hmacHex(phone);
        requirePhoneNotRegistered(phoneHash);
        String masked = MaskUtils.maskPhone(phone);
        String finalNickname = (nickname == null || nickname.isBlank())
                ? "学员" + masked.substring(masked.length() - 4) : nickname.trim();
        jdbcTemplate.update(
                "INSERT INTO student (phone_cipher, phone_hash, password_hash, nickname) VALUES (?, ?, ?, ?)",
                phoneCrypto.encrypt(phone), phoneHash, passwordEncoder.encode(password), finalNickname);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM student WHERE phone_hash = ?", Long.class, phoneHash);
        return issueSession(id, finalNickname, masked, null, true, response);
    }

    /** 密码登录：连续失败达阈值强制验证码登录（30007）；文案统一不暴露手机号是否存在 */
    @Transactional
    public AuthSessionResponse loginByPassword(String phone, String password, HttpServletResponse response) {
        String phoneHash = phoneCrypto.hmacHex(phone);
        if (loginFailureService.failureCount(LoginFailureService.ACCOUNT_TYPE_STUDENT, phoneHash)
                >= C_PASSWORD_FAILURE_THRESHOLD) {
            throw new BizException(ErrorCode.PASSWORD_LOGIN_LOCKED);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, password_hash, nickname, status, current_path_id FROM student WHERE phone_hash = ?",
                phoneHash);
        if (rows.isEmpty() || !passwordEncoder.matches(password, (String) rows.get(0).get("password_hash"))) {
            loginFailureService.recordFailure(LoginFailureService.ACCOUNT_TYPE_STUDENT, phoneHash, false, 0, null);
            throw new BizException(ErrorCode.LOGIN_CREDENTIAL_INVALID);
        }
        Map<String, Object> student = rows.get(0);
        checkStatus(((Number) student.get("status")).intValue());
        loginFailureService.reset(LoginFailureService.ACCOUNT_TYPE_STUDENT, phoneHash);
        return issueSession(((Number) student.get("id")).longValue(), (String) student.get("nickname"),
                MaskUtils.maskPhone(phone),
                student.get("current_path_id") == null ? null : ((Number) student.get("current_path_id")).longValue(),
                true, response);
    }

    /** 验证码登录：验证码已在控制器消费；验证码通过即清零失败计数 */
    @Transactional
    public AuthSessionResponse loginBySms(String phone, HttpServletResponse response) {
        String phoneHash = phoneCrypto.hmacHex(phone);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, nickname, status, current_path_id FROM student WHERE phone_hash = ?", phoneHash);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.LOGIN_CREDENTIAL_INVALID);
        }
        Map<String, Object> student = rows.get(0);
        checkStatus(((Number) student.get("status")).intValue());
        loginFailureService.reset(LoginFailureService.ACCOUNT_TYPE_STUDENT, phoneHash);
        return issueSession(((Number) student.get("id")).longValue(), (String) student.get("nickname"),
                MaskUtils.maskPhone(phone),
                student.get("current_path_id") == null ? null : ((Number) student.get("current_path_id")).longValue(),
                true, response);
    }

    /** 重置密码：改密后撤销该学员全部 refresh token（04 §6.1.5），原因=2 改密 */
    @Transactional
    public void resetPassword(String phone, String newPassword) {
        requirePasswordPolicy(newPassword);
        String phoneHash = phoneCrypto.hmacHex(phone);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM student WHERE phone_hash = ?", Long.class, phoneHash);
        jdbcTemplate.update(
                "UPDATE student SET password_hash = ?, password_reset_required = 0 WHERE id = ?",
                passwordEncoder.encode(newPassword), id);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_STUDENT, id, 2);
    }

    /** 签发 access + refresh，refresh 写 Set-Cookie；响应体只含 token 与主体摘要 */
    private AuthSessionResponse issueSession(long studentId, String nickname, String phoneMasked,
                                             Long currentPathId, boolean hasPassword,
                                             HttpServletResponse response) {
        checkStatus(fetchStatus(studentId));
        JwtTokenProvider.AccessIssuance access = tokenProvider.issueAccessToken(studentId, Audience.C);
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(SUBJECT_TYPE_STUDENT, studentId);
        cookieService.write(response, Audience.C, refresh.token());
        return new AuthSessionResponse(access.token(),
                RFC3339_UTC.format(access.expiresAt()),
                RFC3339_UTC.format(refresh.expiresAt().toInstant(ZoneOffset.UTC)),
                new StudentSummary(studentId, nickname, phoneMasked, currentPathId, hasPassword));
    }

    private int fetchStatus(long studentId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT status FROM student WHERE id = ?", Integer.class, studentId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "学员记录缺失");
        }
        return rows.get(0);
    }

    /** 停用/注销在发凭证前拦截（04 §6.1.3：20005/20006） */
    private void checkStatus(int status) {
        if (status == STATUS_DISABLED) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (status == STATUS_CANCELLED) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLED);
        }
    }

    /** 30005：8–32 位且同时包含字母和数字 */
    private void requirePasswordPolicy(String password) {
        if (password == null || !PASSWORD_RULE.matcher(password).matches()) {
            throw new BizException(ErrorCode.PASSWORD_POLICY);
        }
    }

    private void requirePhoneNotRegistered(String phoneHash) {
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT id FROM student WHERE phone_hash = ?", Long.class, phoneHash);
        if (!rows.isEmpty()) {
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }
}
