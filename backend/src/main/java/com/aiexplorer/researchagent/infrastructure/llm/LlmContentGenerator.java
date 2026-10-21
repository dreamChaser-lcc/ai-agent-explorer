package com.aiexplorer.researchagent.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用 LLM 内容生成器：把"治理三件套"复用给所有需要 LLM 写内容的地方
 * （场景⑧延伸：总结步骤、报告生成）。
 *
 * 与 LlmPlanGenerator 的分工：
 *   - LlmPlanGenerator：懂"计划长什么样"（提示词 + 计划 JSON 结构 + 模板降级）；
 *   - 本类：只懂"怎么安全地要一段文本/一段 JSON"（治理 + 去围栏 + 解析），不懂业务语义。
 *
 * 返回 Optional 而不是抛异常：**调用方必须显式处理"没有结果"的情况**（各自准备降级文案），
 * 这比"catch 异常"更不容易漏掉降级分支。
 */
public class LlmContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmContentGenerator.class);

    private final LlmCallGuard llmCallGuard;
    private final ObjectMapper objectMapper;

    public LlmContentGenerator(LlmCallGuard llmCallGuard, ObjectMapper objectMapper) {
        this.llmCallGuard = llmCallGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成一段纯文本（调用治理层：超时 / 重试 / 降级）。
     *
     * @return 成功返回文本；降级或空内容返回 empty（由调用方决定兜底文案）
     */
    public Optional<String> generateText(String promptText) {
        LlmCallResult callResult = llmCallGuard.call(promptText);
        if (callResult.degraded()) {
            log.warn("LLM 文本生成降级：{}", callResult.degradedReason());
            return Optional.empty();
        }
        String contentText = callResult.contentText();
        if (contentText == null || contentText.isBlank()) {
            log.warn("LLM 返回空内容，按降级处理");
            return Optional.empty();
        }
        return Optional.of(contentText.trim());
    }

    /**
     * 生成一段 JSON（自动去掉模型偶尔附带的 ```json 围栏）。
     *
     * @return 成功返回解析后的 JSON 树；降级或解析失败返回 empty
     */
    public Optional<JsonNode> generateJson(String promptText) {
        Optional<String> textOptional = generateText(promptText);
        if (textOptional.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(stripCodeFence(textOptional.get()));
            return Optional.ofNullable(jsonNode);
        } catch (Exception exception) {
            log.warn("LLM 返回内容不是合法 JSON，按降级处理：{}", exception.getMessage());
            return Optional.empty();
        }
    }

    /** 去掉模型有时会附带的 ```json 代码块围栏。 */
    private String stripCodeFence(String contentText) {
        String trimmed = contentText.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            }
            int fenceEnd = trimmed.lastIndexOf("```");
            if (fenceEnd >= 0) {
                trimmed = trimmed.substring(0, fenceEnd);
            }
        }
        return trimmed.trim();
    }
}
