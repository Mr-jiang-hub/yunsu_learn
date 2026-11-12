package com.yunshu.zhixue.common.auth;

import com.yunshu.zhixue.common.config.AppProperties;
import com.yunshu.zhixue.common.security.Audience;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

import org.springframework.util.StringUtils;

/**
 * Refresh Token Cookie 读写与清除（05 §6 #52 Cookie 规格）。
 *
 * <p>C 端 ys_c_rt（Path=/api/v1/c），B 端 ys_b_rt（Path=/api/v1/admin）；
 * HttpOnly + SameSite=Lax；Max-Age 取 refresh TTL 秒数；
 * Secure 按 profile（dev 关 / prod 开）。</p>
 */
@Service
public class AuthCookieService {

    public static final String C_COOKIE = "ys_c_rt";
    public static final String ADMIN_COOKIE = "ys_b_rt";
    private static final String SAME_SITE = "Lax";

    private final AppProperties properties;
    private final Environment environment;

    public AuthCookieService(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    private boolean secureEnabled() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    private long maxAgeSeconds() {
        return properties.security().refreshTtl().toSeconds();
    }

    public Optional<String> read(HttpServletRequest request, Audience audience) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = name(audience);
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public void write(HttpServletResponse response, Audience audience, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(name(audience), rawToken)
                .httpOnly(true)
                .secure(secureEnabled())
                .sameSite(SAME_SITE)
                .path(path(audience))
                .maxAge(maxAgeSeconds())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** 登出或刷新失败时清除 Cookie（Max-Age=0） */
    public void clear(HttpServletResponse response, Audience audience) {
        ResponseCookie cookie = ResponseCookie.from(name(audience), "")
                .httpOnly(true)
                .secure(secureEnabled())
                .sameSite(SAME_SITE)
                .path(path(audience))
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private static String name(Audience audience) {
        return audience == Audience.C ? C_COOKIE : ADMIN_COOKIE;
    }

    private static String path(Audience audience) {
        return audience == Audience.C ? "/api/v1/c" : "/api/v1/admin";
    }
}
