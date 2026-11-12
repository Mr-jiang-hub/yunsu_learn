package com.yunshu.zhixue.admin.auth;

import com.yunshu.zhixue.admin.auth.dto.AdminAuthSessionResponse;
import com.yunshu.zhixue.admin.auth.dto.AdminMeResponse;
import com.yunshu.zhixue.admin.auth.dto.AdminRoleBrief;
import com.yunshu.zhixue.admin.auth.dto.AdminSummary;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.auth.AuthCookieService;
import com.yunshu.zhixue.common.auth.LoginFailureService;
import com.yunshu.zhixue.common.auth.RefreshTokenService;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.JwtTokenProvider;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.admin.auth.dto.AdminMeResponse;
import com.yunshu.zhixue.common.web.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * B 端认证（04 §6.10.1）：账号密码登录、审计、失败锁定（连续 5 次锁 15 分钟）。
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final DateTimeFormatter RFC3339_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final int SUBJECT_TYPE_ADMIN = 2;
    private static final int ACCOUNT_TYPE_ADMIN = LoginFailureService.ACCOUNT_TYPE_ADMIN;
    private static final int LOCK_THRESHOLD = 5;
    private static final java.time.Duration LOCK_DURATION = java.time.Duration.ofMinutes(15);
    private static final int STATUS_DISABLED = 2;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService cookieService;
    private final LoginFailureService loginFailureService;

    public AdminAuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                            JwtTokenProvider tokenProvider, RefreshTokenService refreshTokenService,
                            AuthCookieService cookieService, LoginFailureService loginFailureService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.cookieService = cookieService;
        this.loginFailureService = loginFailureService;
    }

    /**
     * 登录：锁定检查 → 查账号 → bcrypt 比对 → 状态检查 → 失败计数/清零 →
     * 签发双 token（refresh 走 Set-Cookie）→ 写登录审计 → 返回模块权限。
     */
    @Transactional
    public AdminAuthSessionResponse login(String username, String password, String requestIp,
                                          HttpServletResponse response) {
        loginFailureService.checkNotLocked(ACCOUNT_TYPE_ADMIN, username);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT a.id, a.password_hash, a.display_name, a.role_id, a.status, a.password_reset_required, "
                        + "r.code AS role_code, r.name AS role_name "
                        + "FROM admin_user a JOIN admin_role r ON r.id = a.role_id WHERE a.account = ?",
                username);
        if (rows.isEmpty()
                || !passwordEncoder.matches(password, (String) rows.get(0).get("password_hash"))) {
            loginFailureService.recordFailure(ACCOUNT_TYPE_ADMIN, username, true, LOCK_THRESHOLD, LOCK_DURATION);
            throw new BizException(ErrorCode.ADMIN_LOGIN_CREDENTIAL_INVALID);
        }
        Map<String, Object> admin = rows.get(0);
        long adminId = ((Number) admin.get("id")).longValue();
        if (((Number) admin.get("status")).intValue() == STATUS_DISABLED) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        loginFailureService.reset(ACCOUNT_TYPE_ADMIN, username);

        JwtTokenProvider.AccessIssuance access = tokenProvider.issueAccessToken(adminId, Audience.ADMIN);
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(SUBJECT_TYPE_ADMIN, adminId);
        cookieService.write(response, Audience.ADMIN, refresh.token());

        String roleCode = (String) admin.get("role_code");
        String roleName = (String) admin.get("role_name");
        List<String> modules = loadModules(((Number) admin.get("role_id")).longValue(), roleCode);
        boolean mustChangePassword = truthy(admin.get("password_reset_required"));
        writeLoginAudit(adminId, (String) admin.get("display_name"), roleName, requestIp, username);
        return new AdminAuthSessionResponse(access.token(), RFC3339_UTC.format(access.expiresAt()),
                RFC3339_UTC.format(refresh.expiresAt().toInstant(ZoneOffset.UTC)),
                new AdminSummary(adminId, username, (String) admin.get("display_name"),
                        ((Number) admin.get("role_id")).longValue(), roleCode, roleName),
                modules, mustChangePassword);
    }

    /**
     * 当前登录管理员资料 + 角色 + 模块权限（04 §6.10.1 GET /me）。
     * 账号不存在或已停用视为登录态无效，要求前端重新登录。
     */
    @Transactional(readOnly = true)
    public AdminMeResponse me(long adminId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT a.id, a.account, a.display_name, a.role_id, a.status, "
                        + "r.code AS role_code, r.name AS role_name "
                        + "FROM admin_user a JOIN admin_role r ON r.id = a.role_id WHERE a.id = ?",
                adminId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        Map<String, Object> admin = rows.get(0);
        if (((Number) admin.get("status")).intValue() == STATUS_DISABLED) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        long roleId = ((Number) admin.get("role_id")).longValue();
        String roleCode = (String) admin.get("role_code");
        String roleName = (String) admin.get("role_name");
        return new AdminMeResponse(
                new AdminSummary(adminId, (String) admin.get("account"), (String) admin.get("display_name"),
                        roleId, roleCode, roleName),
                new AdminRoleBrief(roleId, roleCode, roleName),
                loadModules(roleId, roleCode));
    }

    /**
     * TINYINT(1) 列在不同驱动参数下可能映射为 Boolean 或数值，
     * 统一按「非 0 / true」判定，避免 Boolean 转 Number 的 ClassCastException。
     */
    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    /** 模块权限：super_admin 固定全部 12 个业务模块；其余按 admin_role_module */
    private List<String> loadModules(long roleId, String roleCode) {
        if ("super_admin".equals(roleCode)) {
            return List.of("subject", "course", "question", "interview_settings", "learning_path",
                    "student", "admin_user", "role", "operation_log", "dashboard", "notice", "message");
        }
        return jdbcTemplate.queryForList(
                "SELECT module_code FROM admin_role_module WHERE role_id = ? ORDER BY module_code",
                String.class, roleId);
    }

    private void writeLoginAudit(long adminId, String displayName, String roleName,
                                 String requestIp, String username) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO operation_log (operator_id, operator_name, role_name, module_code, action, "
                            + "target_type, target_id, result, detail, ip, trace_id) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)",
                    adminId, displayName, roleName, "admin_user", "login", "admin_user", adminId,
                    "后台登录成功 account=" + username,
                    requestIp == null ? "" : requestIp,
                    com.yunshu.zhixue.common.web.TraceIdFilter.current());
        } catch (Exception ex) {
            log.warn("登录审计写入失败 adminId={} traceId={}", adminId,
                    TraceIdFilter.current(), ex);
        }
    }
}
