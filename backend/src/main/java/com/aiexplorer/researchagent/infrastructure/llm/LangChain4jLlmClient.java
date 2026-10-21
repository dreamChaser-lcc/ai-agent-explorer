package com.aiexplorer.researchagent.infrastructure.llm;

import com.aiexplorer.researchagent.infrastructure.config.LlmProperties;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 基于 LangChain4j 的真实 LLM 客户端（场景⑧ 的默认实现）。
 *
 * 为什么最终回到框架：
 *   - pom 里本来就引了 langchain4j，不用它就是"死依赖"（误导后来人 + 白占 classpath）；
 *   - LangChain4j 1.17.2 提供了 {@code customHeaders(...)}，可以注入中转站要求的 User-Agent，
 *     我们原先"无法设置 UA 所以只能手写"的理由不再成立；
 *   - 后续要用的结构化输出、工具调用（function calling）、RAG 都在框架里，早用早受益。
 *
 * 三个关键配置点（每一个都有踩坑背景）：
 *   1. {@code customHeaders("User-Agent", ...)} —— 中转站网关做客户端指纹检测，默认 UA 直接 401；
 *   2. {@code maxRetries(0)} —— ★ 关闭框架内部重试！否则会与 LlmCallGuard 的重试叠加
 *      （框架重试 2 次 × 治理层重试 3 次 = 最多 9 次调用，把下游打得更惨）；
 *   3. {@code timeout(...)} —— 框架层超时（兜底），业务级超时仍由 Guard 控制。
 */
public class LangChain4jLlmClient implements LlmClient {

    /** 单次响应最大 token 数（计划 JSON 通常 500 以内，留足余量）。 */
    private static final int MAX_OUTPUT_TOKENS = 1200;

    private final ChatModel chatModel;
    private final String implementationDescription;

    public LangChain4jLlmClient(LlmProperties llmProperties) {
        this.chatModel = OpenAiChatModel.builder()
                .baseUrl(normalizeBaseUrl(llmProperties.baseUrl()))
                .apiKey(llmProperties.apiKey())
                .modelName(llmProperties.chatModel())
                .temperature(llmProperties.temperature())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .timeout(Duration.ofSeconds(Math.max(1, llmProperties.timeoutSeconds())))
                // ★ 重试交给 LlmCallGuard 统一管，框架内部重试必须关掉（否则重试叠加）
                .maxRetries(0)
                // ★ 过中转站网关的通行证（缺了就是 401 unauthorized client detected）
                .customHeaders(Map.of("User-Agent", llmProperties.userAgent()))
                // ★ 代理只作用于 LLM 请求（不影响 Nacos 等内网组件）
                .httpClientBuilder(buildHttpClientBuilder(llmProperties))
                .logRequests(false)
                .logResponses(false)
                .build();
        this.implementationDescription = "langchain4j[" + llmProperties.chatModel() + "]";
    }

    /**
     * 构建带"作用域隔离"的 HTTP 客户端：只有 LLM 请求走代理。
     *
     * 为什么不用 JVM 全局代理参数（-Dhttps.proxyHost）：
     *   实测发现全局代理会把 Nacos 的内网地址（192.168.157.129）也代理掉，
     *   导致 Nacos 连不上、应用启动失败——代理必须限定作用域。
     */
    private HttpClientBuilder buildHttpClientBuilder(LlmProperties llmProperties) {
        Duration connectTimeout = Duration.ofSeconds(5);
        Duration readTimeout = Duration.ofSeconds(Math.max(1, llmProperties.timeoutSeconds()));
        SpringRestClientBuilder restClientBuilder = new SpringRestClientBuilder()
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout);

        boolean proxyConfigured = llmProperties.proxyHost() != null
                && !llmProperties.proxyHost().isBlank()
                && llmProperties.proxyPort() > 0;
        if (proxyConfigured) {
            java.net.http.HttpClient jdkHttpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .proxy(ProxySelector.of(new InetSocketAddress(
                            llmProperties.proxyHost(), llmProperties.proxyPort())))
                    .build();
            RestClient.Builder scopedRestClientBuilder = RestClient.builder()
                    .requestFactory(new JdkClientHttpRequestFactory(jdkHttpClient));
            restClientBuilder.restClientBuilder(scopedRestClientBuilder);
        }
        return restClientBuilder;
    }

    @Override
    public String complete(String promptText) {
        // ChatModel.chat(String) 是 1.x 的同步文本接口，直接返回模型输出文本
        return chatModel.chat(promptText);
    }

    @Override
    public String describeImplementation() {
        return implementationDescription;
    }

    /** 去掉末尾斜杠，避免拼出 "//chat/completions"。 */
    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
