package com.yunshu.zhixue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 云枢智学后端启动类。
 *
 * <p>模块化单体：按业务域分包（student / content / learning / interview / admin），
 * 外部依赖经 integration 适配器接入，详见 docs/design/02.技术选型与整体架构.md。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.yunshu.zhixue")
public class YunshuZhixueApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunshuZhixueApplication.class, args);
    }
}
