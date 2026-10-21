package com.aiexplorer.researchagent.infrastructure.llm;

import com.aiexplorer.researchagent.infrastructure.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 真实 LLM 客户端：调用任意 OpenAI 兼容接口（官方 OpenAI / DeepSeek / 中转站）。
 *
 * 三个本项目实测踩出来的关键点：
 *   1. base-url 指向 "{域名}/v1"，请求路径补 "/chat/completions"（场景⑧ 实测：agentrouter.org/v1）；
 *   2. User-Agent 必须显式设置——中转站网关会做客户端指纹检测，默认 Java UA 直接 401
 *      "unauthorized client detected"（实测结论，讲义有完整排障链）；
 *   3. 国内访问需走本机代理（实测：直连超时，经 127.0.0.1:7897 才通），故支持可选 proxyHost/proxyPort。
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    /** 单次响应正文解析失败时，日志里最多保留的字符数（防止刷屏）。 */
    private static final int MAX_ERROR_BODY_LENGTH = 300;

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        // 可选代理：国内直连第三方 API 通常不通（场景⑧ 实测 status=000）
        if (llmProperties.proxyHost() != null && !llmProperties.proxyHost().isBlank()
                && llmProperties.proxyPort() > 0) {
            builder.proxy(ProxySelector.of(
                    new InetSocketAddress(llmProperties.proxyHost(), llmProperties.proxyPort())));
        }
        this.httpClient = builder.build();
    }

    @Override
    public String complete(String promptText) {
        try {
            String requestJson = buildRequestBody(promptText);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(1, llmProperties.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + llmProperties.apiKey())
                    // ★ 过网关的通行证：不设或被识别为"未授权客户端"
                    .header("User-Agent", llmProperties.userAgent())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new IllegalStateException("LLM 返回状态码 " + httpResponse.statusCode()
                        + "，响应：" + abbreviate(httpResponse.body()));
            }
            return extractContent(httpResponse.body());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 调用被中断", interruptedException);
        } catch (IllegalStateException stateException) {
            throw stateException;
        } catch (Exception exception) {
            throw new IllegalStateException("LLM 调用失败：" + exception.getMessage(), exception);
        }
    }

    @Override
    public String describeImplementation() {
        return "openai-compatible[" + llmProperties.chatModel() + "]";
    }

    /** 组装 OpenAI 兼容的 chat/completions 请求体。 */
    private String buildRequestBody(String promptText) throws Exception {
        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("model", llmProperties.chatModel());
        requestNode.put("temperature", llmProperties.temperature());
        requestNode.put("max_tokens", 1200);
        requestNode.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", promptText);
        return objectMapper.writeValueAsString(requestNode);
    }

    /** 从响应里取 choices[0].message.content。 */
    private String extractContent(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IllegalStateException("LLM 响应缺少 choices[0].message.content，原文："
                    + abbreviate(responseBody));
        }
        return contentNode.asText();
    }

    /** 兼容"base-url 是否带 /v1"的两种写法。 */
    private String normalizeBaseUrl() {
        String baseUrl = llmProperties.baseUrl() == null ? "" : llmProperties.baseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= MAX_ERROR_BODY_LENGTH ? text : text.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
