package com.aiexplorer.researchagent.infrastructure.config;

import com.aiexplorer.researchagent.infrastructure.llm.LangChain4jLlmClient;
import com.aiexplorer.researchagent.infrastructure.llm.LlmCallGuard;
import com.aiexplorer.researchagent.infrastructure.llm.LlmClient;
import com.aiexplorer.researchagent.infrastructure.llm.LlmContentGenerator;
import com.aiexplorer.researchagent.infrastructure.llm.OpenAiCompatibleLlmClient;
import com.aiexplorer.researchagent.infrastructure.llm.SimulatedLlmClient;
import com.aiexplorer.researchagent.infrastructure.search.LlmSearchProvider;
import com.aiexplorer.researchagent.infrastructure.search.SearchProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端装配：按配置在"真实客户端"与"模拟客户端"之间切换，并创建治理层。
 *
 * 为什么不用 LangChain4j starter 的自动装配：
 *   中转站网关要求自定义 User-Agent，自动装配出来的 ChatModel 无法注入该请求头
 *   （场景⑧ 实测：默认 UA → 401 unauthorized client detected）。
 *   手写 HttpClient 版本可控性最高，也把"下游调用"的每一层摊开给学员看。
 */
@Configuration
public class LlmClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfiguration.class);

    /** 默认超时（秒）——LLM 首字返回通常在 3 秒内，20 秒是宽松上限。 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;

    /** 默认重试次数。 */
    private static final int DEFAULT_MAX_RETRIES = 2;

    @Bean
    public LlmClient llmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        if (llmProperties.simulated()) {
            log.warn("LLM 使用【模拟客户端】模式：{}（不会发起真实网络请求）",
                    llmProperties.simulateMode());
            return new SimulatedLlmClient(llmProperties);
        }
        String clientImpl = llmProperties.clientImpl() == null
                ? "langchain4j" : llmProperties.clientImpl().trim().toLowerCase();
        if ("http".equals(clientImpl)) {
            log.info("LLM 使用【手写 HttpClient】实现：baseUrl={}，model={}",
                    llmProperties.baseUrl(), llmProperties.chatModel());
            return new OpenAiCompatibleLlmClient(llmProperties, objectMapper);
        }
        log.info("LLM 使用【LangChain4j】实现：baseUrl={}，model={}",
                llmProperties.baseUrl(), llmProperties.chatModel());
        return new LangChain4jLlmClient(llmProperties);
    }

    /**
     * 通用内容生成器：总结步骤与报告生成共用（场景⑧延伸）。
     */
    @Bean
    public LlmContentGenerator llmContentGenerator(LlmCallGuard llmCallGuard, ObjectMapper objectMapper) {
        return new LlmContentGenerator(llmCallGuard, objectMapper);
    }

    /**
     * 搜索提供方：默认 LLM 知识检索（未接真实搜索 API，保证功能完整）。
     *
     * 以后接真实 API（Tavily/Bing/博查/百度……）：
     *   1. 新建实现类实现 {@link SearchProvider}；
     *   2. 在这里按 {@code app.llm.search-provider} 返回对应实现；
     *   3. WebSearchResearchTool 与下游完全不改。
     */
    @Bean
    public SearchProvider searchProvider(LlmProperties llmProperties, LlmContentGenerator llmContentGenerator) {
        String searchProviderType = llmProperties.searchProvider() == null
                ? "llm" : llmProperties.searchProvider().trim().toLowerCase();
        if ("api".equals(searchProviderType)) {
            log.warn("搜索提供方配置为 api，但真实搜索 API 尚未接入——暂时回退为 LLM 知识检索");
        }
        log.info("搜索提供方：【LLM 知识检索】（未接真实搜索 API，来源标记为 llm://knowledge/）");
        return new LlmSearchProvider(llmContentGenerator, llmProperties);
    }

    @Bean
    public LlmCallGuard llmCallGuard(LlmClient llmClient, LlmProperties llmProperties) {
        int timeoutSeconds = llmProperties.timeoutSeconds() > 0
                ? llmProperties.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        int maxRetries = llmProperties.maxRetries() >= 0
                ? llmProperties.maxRetries() : DEFAULT_MAX_RETRIES;
        return new LlmCallGuard(client, timeoutSeconds, maxRetries);
    }
}
