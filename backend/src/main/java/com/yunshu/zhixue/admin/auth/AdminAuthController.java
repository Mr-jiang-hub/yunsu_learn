package com.yunshu.zhixue.admin.auth;

import com.yunshu.zhixue.admin.auth.dto.AdminAuthSessionResponse;
import com.yunshu.zhixue.admin.auth.dto.AdminLoginRequest;
import com.yunshu.zhixue.admin.auth.dto.AdminMeResponse;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.auth.AuthSessionService;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 端认证接口（04 §6.10.1）。
 * refresh/logout 无请求体，凭证走 Cookie ys_b_rt；/me 用于刷新页面时恢复菜单与权限。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AuthSessionService authSessionService;

    public AdminAuthController(AdminAuthService adminAuthService, AuthSessionService authSessionService) {
        this.adminAuthService = adminAuthService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminAuthSessionResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        return ApiResponse.ok(adminAuthService.login(request.username(), request.password(),
                clientIp(httpRequest), response));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<AuthSessionService.RefreshedSession> refresh(HttpServletRequest request,
                                                                    HttpServletResponse response) {
        return ApiResponse.ok(authSessionService.refresh(request, response, Audience.ADMIN));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authSessionService.logout(request, response, Audience.ADMIN);
        return ApiResponse.ok();
    }

    /** 页面刷新后恢复会话：只认 token 里的 adminId，不接受前端传参 */
    @GetMapping("/me")
    public ApiResponse<AdminMeResponse> me() {
        return ApiResponse.ok(adminAuthService.me(CurrentUserContext.require(Audience.ADMIN).id()));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
