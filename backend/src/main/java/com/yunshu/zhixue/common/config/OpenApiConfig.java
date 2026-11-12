package com.yunshu.zhixue.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 文档：契约以 docs/design/04.API接口设计.md 与 openapi/*.yaml 为准，
 * 代码只负责生成与之一致的分组与鉴权声明。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI yunshuOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("云枢智学 API")
                        .version("v1")
                        .description("C 端 /api/v1/c，B 端 /api/v1/admin；统一响应与错误码见 04 文档。"))
                .servers(List.of(new Server().url("/api/v1").description("统一基础路径")))
                .components(new Components()
                        .addSecuritySchemes("cBearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("C 端 access token（audience=c）"))
                        .addSecuritySchemes("adminBearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("B 端 access token（audience=admin）")));
    }
}
