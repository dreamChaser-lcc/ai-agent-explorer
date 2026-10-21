package com.aiexplorer.researchagent.infrastructure.search;

import com.aiexplorer.researchagent.infrastructure.config.LlmProperties;
import com.aiexplorer.researchagent.infrastructure.llm.LlmContentGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认搜索提供方：**未接搜索 API 时，由 LLM 基于自身知识生成候选资料**。
 *
 * 定位与诚实性约定：
 *   - 这不是"搜索"，而是"让模型回忆它知道的内容"——能保证功能完整（有资料可总结），
 *     但**信息可能过时、无法溯源**，所以 url 统一用 {@code llm://knowledge/N} 伪协议，
 *     下游报告会明确标注"LLM 知识（未接真实搜索）"；
 *   - 真正的可溯源检索，等接入真实 API 后替换本实现即可（见 {@link SearchProvider} 注释）。
 *
 * 降级：LLM 不可用 / 返回不可解析 → 返回空列表，由 WebSearchResearchTool 回退到内置示例数据。
 */
public class LlmSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(LlmSearchProvider.class);

    /** 生成候选资料的条数。 */
    private static final int TARGET_HIT_COUNT = 3;

    /** LLM 知识来源的伪协议前缀（明确标记"非真实网页"）。 */
    public static final String LLM_KNOWLEDGE_URL_PREFIX = "llm://knowledge/";

    private final LlmContentGenerator llmContentGenerator;
    private final LlmProperties llmProperties;

    public LlmSearchProvider(LlmContentGenerator llmContentGenerator, LlmProperties llmProperties) {
        this.llmContentGenerator = llmContentGenerator;
        this.llmProperties = llmProperties;
    }

    @Override
    public List<SearchHit> search(String query) {
        String promptText = buildSearchPrompt(query);
        Optional<JsonNode> jsonOptional = llmContentGenerator.generateJson(promptText);
        if (jsonOptional.isEmpty() || !jsonOptional.get().isArray()) {
            log.warn("LLM 知识检索失败或格式不符，返回空结果（由搜索工具降级）");
            return List.of();
        }

        List<SearchHit> hits = new ArrayList<>();
        int index = 1;
        for (JsonNode node : jsonOptional.get()) {
            String title = node.path("title").asText("").trim();
            String summary = node.path("summary").asText("").trim();
            if (title.isBlank() || summary.isBlank()) {
                continue;
            }
            double relevance = node.path("relevance").asDouble(0.9);
            // 伪协议：明确不是真实网页，报告里会标注"LLM 知识"
            String knowledgeUrl = LLM_KNOWLEDGE_URL_PREFIX + index;
            hits.add(new SearchHit(title, knowledgeUrl, summary, relevance));
            index++;
        }
        log.info("LLM 知识检索完成：{} 条候选资料（目标 {} 条）", hits.size(), TARGET_HIT_COUNT);
        return hits;
    }

    @Override
    public String describe() {
        return "llm-knowledge[" + llmProperties.chatModel() + "]";
    }

    /**
     * 组装"知识检索"提示词。
     *
     * 注意：明确要求"不提供链接"，避免模型编造 URL（幻觉链接比没有链接更糟）。
     */
    private String buildSearchPrompt(String query) {
        return """
                你是研究资料检索器。请围绕研究目标，列出你知识范围内最相关的 %d 个资料条目。

                研究目标：%s

                输出要求（严格遵守）：
                1. 只输出一个 JSON 数组，不要解释文字，不要 markdown 代码块；
                2. 结构：[{"title":"资料标题","summary":"60 字以内的内容要点","relevance":0.9}]
                3. 输出 %d 条，relevance 为 0-1 之间的相关性评分；
                4. 不要编造网址（本系统暂时没有真实搜索，链接由系统统一标记为"LLM 知识"）；
                5. 全部使用中文。
                """.formatted(TARGET_HIT_COUNT, query == null ? "" : query, TARGET_HIT_COUNT);
    }
}
