package com.yunshu.zhixue.common.security;

import com.yunshu.zhixue.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Bearer access token 并写入 SecurityContext。
 * 每个端各有一个实例，绑定自己的 audience。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AccountStatusVerifier accountStatusVerifier;
    private final Audience audience;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   AccountStatusVerifier accountStatusVerifier,
                                   Audience audience,
                                   SecurityErrorWriter errorWriter) {
        this.tokenProvider = tokenProvider;
        this.accountStatusVerifier = accountStatusVerifier;
        this.audience = audience;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            AuthPrincipal principal = tokenProvider.parseAccessToken(token, audience);
            accountStatusVerifier.verify(principal);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(audience.authority())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BizException ex) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, ex.getErrorCode(), ex.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
