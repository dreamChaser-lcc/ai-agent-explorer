package com.aiexplorer.researchagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 Research Agent 使用的模型与供应商配置。
 *
 * 场景⑧（下游调用治理）扩展：新增 base-url / api-key / user-agent / 超时 / 重试 / 模拟开关。
 * - 为什么自带 base-url 与 user-agent：中转站网关（OneAPI/New-API）会校验客户端指纹，
 *   默认 Java UA 会被判为"未授权客户端"直接 401，必须显式设置。
 * - 为什么有 simulated / simulate-mode：真实下游无法稳定地"慢 5 秒"或"返回 500"，
 *   用可控模拟客户端才能验证超时 / 重试 / 降级三条链路。
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String chatModel,
        String userAgent,
        double temperature,
        int maxSteps,
        int timeoutSeconds,
        int maxRetries,
        boolean simulated,
        String simulateMode,
        String proxyHost,
        int proxyPort,
        String clientImpl,
        String searchProvider) {
}
