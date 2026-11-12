package com.yunshu.zhixue.common.security;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 读取当前请求的登录主体。业务代码不得从 body/query 接收 studentId / adminId。
 */
public final class CurrentUserContext {

    private CurrentUserContext() {
    }

    public static AuthPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    public static AuthPrincipal require(Audience audience) {
        AuthPrincipal principal = require();
        if (principal.audience() != audience) {
            throw new BizException(ErrorCode.TOKEN_AUDIENCE_MISMATCH);
        }
        return principal;
    }
}
