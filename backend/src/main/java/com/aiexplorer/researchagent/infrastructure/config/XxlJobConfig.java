package com.aiexplorer.researchagent.infrastructure.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T9·XXL-Job 执行器配置（默认关闭——demo 不影响现有启动）。
 *
 * <p>启用方式：application.yml 里 {@code xxl.job.enabled=true} 且调度中心已启动；
 * 关闭时本 Bean 不创建，应用行为与未接入时完全一致（@ConditionalOnProperty 让位机制）。</p>
 *
 * <p>执行器职责：启动时自动注册到调度中心（appname + ip:port），
 * 接收调度中心 REST 派单 → 起线程执行 {@code @XxlJob} 注解方法 → 回调结果。</p>
 */
@Configuration
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true")
public class XxlJobConfig {

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor(
            @Value("${xxl.job.admin-addresses}") String adminAddresses,
            @Value("${xxl.job.access-token}") String accessToken,
            @Value("${xxl.job.executor.appname}") String appname,
            @Value("${xxl.job.executor.port}") int port,
            @Value("${xxl.job.executor.logpath}") String logPath,
            @Value("${xxl.job.executor.logretentiondays}") int logRetentionDays) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
