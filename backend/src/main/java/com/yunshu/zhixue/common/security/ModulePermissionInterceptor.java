package com.yunshu.zhixue.common.security;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一拦截 @RequireModule：B 端接口声明所属模块，无权限返回 403 + 20004。
 */
@Component
public class ModulePermissionInterceptor implements HandlerInterceptor {

    private final ModulePermissionChecker permissionChecker;

    public ModulePermissionInterceptor(ModulePermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireModule annotation = handlerMethod.getMethodAnnotation(RequireModule.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireModule.class);
        }
        if (annotation == null) {
            return true;
        }

        AuthPrincipal principal = CurrentUserContext.require(Audience.ADMIN);
        if (!permissionChecker.hasModule(principal.id(), annotation.value())) {
            throw new BizException(ErrorCode.NO_MODULE_PERMISSION);
        }
        return true;
    }
}
