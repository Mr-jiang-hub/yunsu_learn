package com.yunshu.zhixue.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * JSON 约定：时间统一 UTC / RFC 3339；null 字段保留；未知字段拒绝（04 文档 1.2）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .timeZone(TimeZone.getTimeZone("UTC"))
                .serializationInclusion(JsonInclude.Include.ALWAYS)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
