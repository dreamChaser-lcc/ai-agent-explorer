package com.aiexplorer.researchagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 Research Agent 使用的模型与供应商配置。
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String provider,
        String chatModel,
        double temperature,
        int maxSteps) {
}
