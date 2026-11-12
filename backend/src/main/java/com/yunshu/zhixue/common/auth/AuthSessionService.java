package com.yunshu.zhixue.common.auth;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.JdbcAccountStatusVerifier;
import com.yunshu.zhixue.common.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * C/B 共用的刷新与登出流程（04 §4.2 / §6.1.6 / §6.1.7 / 05 §6 #52 Cookie 方案）。
 * 刷新：Cookie 读 raw → 哈希比对轮换 → 账号状态复检 → 签发新 access + 重发 Cookie；
 * 任一步失败都随响应清除 Cookie。登出：撤销该条 refresh（幂等）+ Max-Age=0 清 Cookie。
 */
@Service
public class AuthSessionService {

    private static final DateTimeFormatter RFC3339_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final AuthCookieService cookieService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider tokenProvider;
    private final JdbcTemplate jdbcTemplate;

    public AuthSessionService(AuthCookieService cookieService, RefreshTokenService refreshTokenService,
                              JwtTokenProvider tokenProvider, JdbcTemplate jdbcTemplate) {
        this.cookieService = cookieService;
        this.refreshTokenService = refreshTokenService;
        this.tokenProvider = tokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 刷新成功后的会话数据（响应体不含 refresh，refresh 在 Set-Cookie 中） */
    public record RefreshedSession(String accessToken, String accessExpiresAt, String refreshExpiresAt) {
    }

    public RefreshedSession refresh(HttpServletRequest request, HttpServletResponse response, Audience audience) {
        String raw = cookieService.read(request, audience).orElse(null);
        if (raw == null) {
            cookieService.clear(response, audience);
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        try {
            RefreshTokenService.IssuedRefresh issued = refreshTokenService.rotate(raw, audience);
            verifyAccountStatus(issued, audience);
            JwtTokenProvider.AccessIssuance access = tokenProvider.issueAccessToken(issued.subjectId(), audience);
            cookieService.write(response, audience, issued.token());
            return new RefreshedSession(access.token(), rfc3339(access.expiresAt()),
                    rfc3339(issued.expiresAt().toInstant(ZoneOffset.UTC)));
        } catch (BizException ex) {
            cookieService.clear(response, audience);
            throw ex;
        }
    }

    /** 登出：幂等撤销该条 refresh 并清 Cookie；Cookie 缺失也返回成功 */
    public void logout(HttpServletRequest request, HttpServletResponse response, Audience audience) {
        cookieService.read(request, audience)
                .ifPresent(refreshTokenService::revoke);
        cookieService.clear(response, audience);
    }

    /**
     * 刷新时复检账号状态（04 §8.6）：停用/注销立即撤销该主体全部 refresh 并返回对应错误码。
     * 不走 60 秒缓存——刷新是签发新凭证的关键路径，必须实时。
     */
    private void verifyAccountStatus(RefreshTokenService.IssuedRefresh issued, Audience audience) {
        int status = switch (audience) {
            case C -> jdbcTemplate.queryForObject(
                    "SELECT status FROM student WHERE id = ?", Integer.class, issued.subjectId());
            case ADMIN -> jdbcTemplate.queryForObject(
                    "SELECT status FROM admin_user WHERE id = ?", Integer.class, issued.subjectId());
        };
        boolean reject = switch (audience) {
            case C -> status == 2 || status == 3;
            case ADMIN -> status == 2;
        };
        if (reject) {
            refreshTokenService.revokeAllFor(issued.subjectType(), issued.subjectId(),
                    status == 2 ? 3 : 4);
            throw new BizException(status == 2 ? ErrorCode.ACCOUNT_DISABLED : ErrorCode.ACCOUNT_CANCELLED);
        }
    }

    private static String rfc3339(Instant instant) {
        return RFC3339_UTC.format(instant);
    }
}
