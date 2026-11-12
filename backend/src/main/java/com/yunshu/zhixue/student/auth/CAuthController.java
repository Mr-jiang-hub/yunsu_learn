package com.yunshu.zhixue.student.auth;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.auth.AuthSessionService;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.student.auth.dto.AuthSessionResponse;
import com.yunshu.zhixue.student.auth.dto.PasswordLoginRequest;
import com.yunshu.zhixue.student.auth.dto.PasswordResetRequest;
import com.yunshu.zhixue.student.auth.dto.RegisterRequest;
import com.yunshu.zhixue.student.auth.dto.SendSmsCodeRequest;
import com.yunshu.zhixue.student.auth.dto.SendSmsCodeResponse;
import com.yunshu.zhixue.student.auth.dto.SmsLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端认证接口（04 §6.1）。refresh/logout 无请求体，凭证走 Cookie ys_c_rt。
 */
@RestController
@RequestMapping("/api/v1/c/auth")
public class CAuthController {

    private final SmsCodeService smsCodeService;
    private final StudentAuthService studentAuthService;
    private final AuthSessionService authSessionService;

    public CAuthController(SmsCodeService smsCodeService, StudentAuthService studentAuthService,
                           AuthSessionService authSessionService) {
        this.smsCodeService = smsCodeService;
        this.studentAuthService = studentAuthService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/sms-codes")
    public ApiResponse<SendSmsCodeResponse> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(smsCodeService.send(request.phone(), request.purpose(),
                clientIp(httpRequest)));
    }

    @PostMapping("/register")
    public ApiResponse<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletResponse response) {
        smsCodeService.verify(request.phone(), SmsPurpose.REGISTER, request.code());
        return ApiResponse.ok(studentAuthService.register(request.phone(), request.password(),
                request.nickname(), response));
    }

    @PostMapping("/login/password")
    public ApiResponse<AuthSessionResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request,
                                                            HttpServletResponse response) {
        return ApiResponse.ok(studentAuthService.loginByPassword(request.phone(), request.password(), response));
    }

    @PostMapping("/login/sms")
    public ApiResponse<AuthSessionResponse> loginBySms(@Valid @RequestBody SmsLoginRequest request,
                                                       HttpServletResponse response) {
        smsCodeService.verify(request.phone(), SmsPurpose.LOGIN, request.code());
        return ApiResponse.ok(studentAuthService.loginBySms(request.phone(), response));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        smsCodeService.verify(request.phone(), SmsPurpose.RESET_PASSWORD, request.code());
        studentAuthService.resetPassword(request.phone(), request.newPassword());
        return ApiResponse.ok();
    }

    @PostMapping("/token/refresh")
    public ApiResponse<AuthSessionService.RefreshedSession> refresh(HttpServletRequest request,
                                                                    HttpServletResponse response) {
        return ApiResponse.ok(authSessionService.refresh(request, response, Audience.C));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authSessionService.logout(request, response, Audience.C);
        return ApiResponse.ok();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
