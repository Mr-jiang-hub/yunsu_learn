package com.yunshu.zhixue.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 为每个请求生成/透传 traceId，写入 MDC 与响应头 X-Trace-Id，
 * 供统一响应体、访问日志与操作日志共用同一个追踪 ID。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 当前请求的 traceId；不在请求线程内时返回空串，保证响应字段不缺失。
     */
    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "" : traceId;
    }

    private static String resolveTraceId(String incoming) {
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        char[] chars = new char[16];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}
