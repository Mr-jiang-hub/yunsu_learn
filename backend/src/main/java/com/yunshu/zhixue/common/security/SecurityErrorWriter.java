package com.yunshu.zhixue.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全过滤器链内的异常不会进入 @RestControllerAdvice，
 * 这里按统一响应结构输出 401 / 403。
 */
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(response, ErrorCode.UNAUTHENTICATED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorCode.NO_MODULE_PERMISSION);
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode));
    }

    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode, message, null));
    }
}
