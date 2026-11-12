package com.yunshu.zhixue.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * C/B 双认证链（02 §6.1）：
 * 1. C 端只接受 audience=c 的 token；
 * 2. B 端只接受 audience=admin 的 token；
 * 3. 其余路径默认拒绝，只开放健康检查与 OpenAPI 文档。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_C_PATHS = {"/api/v1/c/auth/**"};
    private static final String[] PUBLIC_ADMIN_PATHS = {"/api/v1/admin/auth/**"};
    private static final String[] PUBLIC_INFRA_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/error"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain cSecurityFilterChain(HttpSecurity http,
                                                    JwtTokenProvider tokenProvider,
                                                    AccountStatusVerifier accountStatusVerifier,
                                                    SecurityErrorWriter errorWriter) throws Exception {
        http.securityMatcher("/api/v1/c/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_C_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(errorWriter.entryPoint())
                        .accessDeniedHandler(errorWriter.accessDeniedHandler()))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, accountStatusVerifier,
                        Audience.C, errorWriter), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                        JwtTokenProvider tokenProvider,
                                                        AccountStatusVerifier accountStatusVerifier,
                                                        SecurityErrorWriter errorWriter) throws Exception {
        http.securityMatcher("/api/v1/admin/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ADMIN_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(errorWriter.entryPoint())
                        .accessDeniedHandler(errorWriter.accessDeniedHandler()))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, accountStatusVerifier,
                        Audience.ADMIN, errorWriter), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          SecurityErrorWriter errorWriter) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_INFRA_PATHS).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(errorWriter.entryPoint())
                        .accessDeniedHandler(errorWriter.accessDeniedHandler()));
        return http.build();
    }
}
