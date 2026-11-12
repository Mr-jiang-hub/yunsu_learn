package com.yunshu.zhixue.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 只用于通用 CRUD、分页与条件构造；统计与批量使用显式 SQL。
 * 禁止开启自动 DDL，数据库结构只由 Flyway 迁移脚本维护。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 与 api-rules 第 7 章一致：size 上限 100
        pagination.setMaxLimit(100L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
