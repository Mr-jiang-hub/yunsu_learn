package com.yunshu.zhixue.common.web;

import com.yunshu.zhixue.common.config.AppProperties;
import com.yunshu.zhixue.common.security.ModulePermissionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 层横切配置：CORS 白名单与 B 端模块权限拦截器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;
    private final ModulePermissionInterceptor modulePermissionInterceptor;

    public WebConfig(AppProperties properties, ModulePermissionInterceptor modulePermissionInterceptor) {
        this.properties = properties;
        this.modulePermissionInterceptor = modulePermissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(modulePermissionInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().originList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(TraceIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
