package com.yunshu.zhixue.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。目前只有一个旁路任务：孤儿文件每日巡检（04-Q47）。
 *
 * <p>巡检只打日志、不删对象，且自身捕获异常，不允许影响主营业务。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
