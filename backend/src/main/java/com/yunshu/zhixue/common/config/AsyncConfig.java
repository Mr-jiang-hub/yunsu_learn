package com.yunshu.zhixue.common.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 有界异步线程池：用于题库批量导入等低频任务（02 §2.7 / §5.3）。
 * 拒绝策略使用 AbortPolicy，由业务把任务标记为失败并提示重试，避免无限排队。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    public static final String APPLICATION_EXECUTOR = "applicationTaskExecutor";

    @Bean(name = APPLICATION_EXECUTOR)
    public ThreadPoolTaskExecutor applicationTaskExecutor(AppProperties properties) {
        AppProperties.Async async = properties.async();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.corePoolSize());
        executor.setMaxPoolSize(async.maxPoolSize());
        executor.setQueueCapacity(async.queueCapacity());
        executor.setThreadNamePrefix("yunshu-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 把请求线程的 MDC（含 traceId）传递到异步线程，避免日志丢失追踪上下文。
     */
    static final class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}
