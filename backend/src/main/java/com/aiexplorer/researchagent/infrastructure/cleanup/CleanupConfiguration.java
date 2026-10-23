package com.aiexplorer.researchagent.infrastructure.cleanup;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 场景⑩：开启调度 + 注册清理配置。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CleanupProperties.class)
public class CleanupConfiguration {
}