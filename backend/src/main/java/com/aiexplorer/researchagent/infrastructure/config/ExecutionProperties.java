package com.aiexplorer.researchagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定任务编排与异步处理所需的运行期执行配置。
 */
@ConfigurationProperties(prefix = "app.execution")
public record ExecutionProperties(
        String defaultMode,
        int asyncThreadPoolSize,
        boolean pauseAtStepBoundaryOnly) {
}
