package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.infrastructure.llm.LlmContentGenerator;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 引用提取工具（2026-10-20 升级：从"资料列表"到"逐字关键句"）。
 *
 * 旧实现只输出 标题/URL/摘要（等于"资料清单"，不是"引用"）。
 * 新实现：对每条资料的正文，用 LLM 摘出**逐字来自正文**的关键句（quote），
 * 报告里以引用块展示，读起来才像真实研究报告。
 *
 * 核心约束：**quote 必须逐字来自正文**（提示词强约束 + 降级时取正文首句），
 * 绝不允许模型改写或编造——引用的价值在于"可回查原文"。
 *
 * 降级：LLM 不可用/解析失败 → 取正文首句作为 quote（标记 first-sentence-fallback）。
 */
@Component
public class CitationExtractResearchTool implements ResearchTool {

    private static final Logger log = LoggerFactory.getLogger(CitationExtractResearchTool.class);

    /** 最多提取几条引用。 */
    private static final int MAX_CITATION_COUNT = 3;

    /** 送给 LLM 的正文截断长度（字符）。 */
    private static final int MAX_RAW_CONTENT_LENGTH = 500;

    /** 降级时取首句的最大长度。 */
    private static final int MAX_FALLBACK_QUOTE_LENGTH = 90;

    private final ObjectMapper objectMapper;
    private final LlmContentGenerator llmContentGenerator;

    public CitationExtractResearchTool(ObjectMapper objectMapper, LlmContentGenerator llmContentGenerator) {
        this.objectMapper = objectMapper;
        this.llmContentGenerator = llmContentGenerator;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.CITATION_EXTRACT;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        // 候选：已抓取正文的资料；按 URL 去重（SEARCH/FETCH 各插一次会产生重复）
        Set<String> seenUrls = new LinkedHashSet<>();
        List<SourceDocumentEntity> candidates = context.existingSources().stream()
                .filter(source -> source.getSourceType() == SourceType.FETCHED_PAGE)
                .filter(source -> source.getUrl() != null && seenUrls.add(source.getUrl()))
                .limit(MAX_CITATION_COUNT)
                .toList();

        if (candidates.isEmpty()) {
            return new ResearchToolResult(
                    objectMapper.valueToTree(Map.of(
                            "citationCount", 0,
                            "citations", List.of(),
                            "generator", "none"
                    )),
                    List.of());
        }

        // ① 优先：LLM 从正文摘关键句
        Map<Integer, String> quotes = extractQuotes(context.task().getGoal(), candidates);
        String generator = quotes.isEmpty() ? "first-sentence-fallback" : "llm-extracted";

        // ② 组装引用（LLM 失败 → 正文首句兜底）
        List<Map<String, Object>> citations = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            SourceDocumentEntity source = candidates.get(index);
            int sourceIndex = index + 1;
            String quote = quotes.get(sourceIndex);
            boolean fromLlm = quote != null && !quote.isBlank();
            if (!fromLlm) {
                quote = fallbackQuote(source);
            }
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("title", source.getTitle());
            citation.put("url", source.getUrl());
            citation.put("quote", quote);
            citation.put("quoteSource", fromLlm ? "llm-extracted" : "first-sentence-fallback");
            citation.put("snippet", source.getSnippet());
            citations.add(citation);
        }

        log.info("引用提取：{} 条（来源方式：{}）", citations.size(), generator);
        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "citationCount", citations.size(),
                        "citations", citations,
                        "generator", generator
                )),
                List.of());
    }

    /**
     * 调 LLM 摘取逐字关键句，返回 "序号 → 引文" 映射；失败返回空 Map。
     */
    private Map<Integer, String> extractQuotes(String researchGoal, List<SourceDocumentEntity> candidates) {
        String promptText = buildCitationPrompt(researchGoal, candidates);
        Optional<JsonNode> jsonOptional = llmContentGenerator.generateJson(promptText);
        if (jsonOptional.isEmpty()) {
            log.warn("引用提取：LLM 不可用，使用正文首句兜底");
            return Map.of();
        }
        JsonNode quotesNode = jsonOptional.get().path("quotes");
        if (!quotesNode.isArray()) {
            log.warn("引用提取：LLM 返回格式不符（缺少 quotes 数组），使用首句兜底");
            return Map.of();
        }
        Map<Integer, String> quotes = new HashMap<>();
        for (JsonNode quoteNode : quotesNode) {
            int index = quoteNode.path("index").asInt(0);
            String quote = quoteNode.path("quote").asText("").trim();
            if (index > 0 && !quote.isBlank()) {
                quotes.put(index, quote);
            }
        }
        return quotes;
    }

    /**
     * 组装引用提取提示词——重点是"逐字、不得改写、不得编造"三条硬约束。
     */
    private String buildCitationPrompt(String researchGoal, List<SourceDocumentEntity> candidates) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是引用提取器。下面是若干资料的正文，请为每条资料摘出最能支撑研究目标的关键原文句。\n\n");
        promptBuilder.append("研究目标：").append(researchGoal == null ? "" : researchGoal).append("\n\n");
        promptBuilder.append("资料清单：\n");
        for (int index = 0; index < candidates.size(); index++) {
            SourceDocumentEntity source = candidates.get(index);
            promptBuilder.append(index + 1).append(". 标题：")
                    .append(source.getTitle() == null ? "（无标题）" : source.getTitle())
                    .append("\n   正文：");
            String rawContent = source.getRawContent() != null && !source.getRawContent().isBlank()
                    ? source.getRawContent() : source.getSnippet();
            promptBuilder.append(rawContent == null ? "（无正文）" : truncate(rawContent, MAX_RAW_CONTENT_LENGTH))
                    .append("\n");
        }
        promptBuilder.append("""

                输出要求（严格遵守）：
                1. 只输出一个 JSON 对象，不要解释文字，不要 markdown 代码块；
                2. 结构：{"quotes":[{"index":1,"quote":"逐字摘取的句子"}]}
                3. quote 必须【逐字】来自对应资料的正文：不得改写、不得拼接、不得编造；
                4. 每条资料摘 1 句，长度 15-60 字；如果正文里找不到合适句子，可以省略该条；
                5. 全部使用中文。
                """);
        return promptBuilder.toString();
    }

    /**
     * 降级：取正文首句（按句号/问号/感叹号/换行切分），最长 90 字。
     */
    private String fallbackQuote(SourceDocumentEntity source) {
        String rawContent = source.getRawContent() != null && !source.getRawContent().isBlank()
                ? source.getRawContent() : source.getSnippet();
        if (rawContent == null || rawContent.isBlank()) {
            return "（正文缺失）";
        }
        String firstSentence = rawContent.split("[。！？\\n]")[0].trim();
        return truncate(firstSentence, MAX_FALLBACK_QUOTE_LENGTH);
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
