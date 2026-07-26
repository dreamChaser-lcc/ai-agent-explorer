package com.aiexplorer.researchagent.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 提供研究任务执行所需的异步线程池。
 */
@Configuration
public class AsyncExecutionConfiguration {

    /**
     * 提供研究任务异步执行所使用的线程池。
     */
    @Bean(name = "researchTaskExecutor")
    public Executor researchTaskExecutor(ExecutionProperties executionProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("research-task-");
        executor.setCorePoolSize(executionProperties.asyncThreadPoolSize());
        executor.setMaxPoolSize(executionProperties.asyncThreadPoolSize());
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
