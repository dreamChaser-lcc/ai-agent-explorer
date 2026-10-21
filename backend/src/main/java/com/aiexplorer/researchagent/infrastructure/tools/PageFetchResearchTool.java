package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.infrastructure.llm.LlmContentGenerator;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 页面抓取研究工具（场景⑧延伸改造）。
 *
 * 职责：执行 FETCH 步骤——把 SEARCH 得到的候选资料变成"有正文"的资料，供总结步骤使用。
 *
 * 三档来源的处理策略：
 *   ① LLM_KNOWLEDGE（默认，未接真实搜索）：调 LLM 把资料要点展开成 150-250 字正文
 *      （相当于"把模型知道的内容写成一篇短文"）；LLM 失败 → 用摘要兜底；
 *   ② SEARCH_RESULT（未来接真实 API 后）：当前仍用占位正文（真实 HTTP 抓取留待后续），
 *      但优先使用搜索结果自带的摘要，避免完全空转；
 *   ③ 无候选资料 → 返回空结果，由编排器照常推进（步骤不会失败）。
 *
 * 为什么"抓取"也要走 LLM：没有真实 URL 可抓时，让模型把要点扩写成正文，
 * 才能让下游"总结"有足够材料——这是"功能完整性优先"的务实选择，报告里会标注来源性质。
 */
@Component
public class PageFetchResearchTool implements ResearchTool {

    private static final Logger log = LoggerFactory.getLogger(PageFetchResearchTool.class);

    /** 单次最多处理的资料条数（控制 token 与步骤耗时）。 */
    private static final int MAX_FETCH_COUNT = 3;

    private final ObjectMapper objectMapper;
    private final LlmContentGenerator llmContentGenerator;

    public PageFetchResearchTool(ObjectMapper objectMapper, LlmContentGenerator llmContentGenerator) {
        this.objectMapper = objectMapper;
        this.llmContentGenerator = llmContentGenerator;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.FETCH;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        // 候选资料：真实搜索结果 + LLM 知识，都参与"抓取"
        List<SourceDocumentEntity> candidates = context.existingSources().stream()
                .filter(source -> source.getSourceType() == SourceType.SEARCH_RESULT
                        || source.getSourceType() == SourceType.LLM_KNOWLEDGE)
                .limit(MAX_FETCH_COUNT)
                .toList();

        if (candidates.isEmpty()) {
            return new ResearchToolResult(
                    objectMapper.valueToTree(Map.of(
                            "fetchedCount", 0,
                            "message", "没有可抓取的候选资料"
                    )),
                    List.of());
        }

        // 一次 LLM 调用批量展开正文（按序号映射）；失败则为空 Map → 用摘要兜底
        Map<Integer, String> expandedContents = expandContents(context.task().getGoal(), candidates);

        // 按候选顺序传入序号（1 开始），与 LLM 返回的 index 一一对应
        List<ToolSourceDocument> fetchedSources = new java.util.ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            fetchedSources.add(toFetchedDocument(candidates.get(index), index + 1, expandedContents));
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("fetchedCount", fetchedSources.size());
        payload.put("contentMode", expandedContents.isEmpty() ? "snippet-fallback" : "llm-expanded");
        payload.put("message", expandedContents.isEmpty()
                ? "已用资料摘要作为正文（LLM 展开不可用）"
                : "已展开资料正文");
        return new ResearchToolResult(objectMapper.valueToTree(payload), fetchedSources);
    }

    /**
     * 调 LLM 批量展开正文，返回 "序号 → 正文" 映射；失败返回空 Map。
     */
    private Map<Integer, String> expandContents(String researchGoal, List<SourceDocumentEntity> candidates) {
        String promptText = buildExpandPrompt(researchGoal, candidates);
        Optional<JsonNode> jsonOptional = llmContentGenerator.generateJson(promptText);
        if (jsonOptional.isEmpty()) {
            log.warn("抓取步骤：LLM 展开正文失败，将使用摘要兜底");
            return Map.of();
        }
        JsonNode contentsNode = jsonOptional.get().path("contents");
        if (!contentsNode.isArray()) {
            log.warn("抓取步骤：LLM 返回格式不符（缺少 contents 数组），使用摘要兜底");
            return Map.of();
        }
        Map<Integer, String> expandedContents = new HashMap<>();
        for (JsonNode itemNode : contentsNode) {
            int index = itemNode.path("index").asInt(0);
            String content = itemNode.path("content").asText("").trim();
            if (index > 0 && !content.isBlank()) {
                expandedContents.put(index, content);
            }
        }
        log.info("抓取步骤：LLM 展开正文成功，{} 条", expandedContents.size());
        return expandedContents;
    }

    /**
     * 组装"展开正文"提示词。
     */
    private String buildExpandPrompt(String researchGoal, List<SourceDocumentEntity> candidates) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是资料整理员。请为下列每条资料整理一段 150-250 字的正文说明。\n\n");
        promptBuilder.append("研究目标：").append(researchGoal == null ? "" : researchGoal).append("\n\n");
        promptBuilder.append("资料清单：\n");
        for (int index = 0; index < candidates.size(); index++) {
            SourceDocumentEntity source = candidates.get(index);
            promptBuilder.append(index + 1).append(". 标题：")
                    .append(source.getTitle() == null ? "（无标题）" : source.getTitle())
                    .append("\n   要点：")
                    .append(source.getSnippet() == null ? "（无摘要）" : source.getSnippet())
                    .append("\n");
        }
        promptBuilder.append("""

                输出要求（严格遵守）：
                1. 只输出一个 JSON 对象，不要解释文字，不要 markdown 代码块；
                2. 结构：{"contents":[{"index":1,"content":"正文..."},{"index":2,"content":"正文..."}]}
                3. index 与上面的资料编号一一对应；
                4. 正文要围绕研究目标展开，说明概念、关键点、适用场景或注意事项，不要编造具体数据；
                5. 全部使用中文。
                """);
        return promptBuilder.toString();
    }

    /**
     * 单条资料 → 已抓取文档。
     *
     * 正文优先级：LLM 展开内容 > 搜索结果摘要 > 内置占位文本（最后的兜底）。
     */
    private ToolSourceDocument toFetchedDocument(
            SourceDocumentEntity source, int sourceIndex, Map<Integer, String> expandedContents) {
        String rawContent = resolveRawContent(source, sourceIndex, expandedContents);
        String domain = source.getDomain() != null
                ? source.getDomain()
                : (source.getUrl() != null && source.getUrl().startsWith("http")
                        ? URI.create(source.getUrl()).getHost()
                        : "llm-knowledge");

        return new ToolSourceDocument(
                SourceType.FETCHED_PAGE,
                source.getUrl(),
                domain,
                source.getTitle(),
                source.getSnippet(),
                rawContent,
                Integer.toHexString((String.valueOf(source.getUrl()) + rawContent).hashCode()),
                "zh-CN",
                FetchStatus.SUCCESS,
                source.getRelevanceScore(),
                false,
                objectMapper.valueToTree(Map.of(
                        "originSourceId", source.getId(),
                        "provider", "llm-expanded-content"
                ))
        );
    }

    /**
     * 解析正文：LLM 展开（按序号精确匹配）→ 摘要 → 占位文本。
     *
     * @param sourceIndex 该资料在候选列表中的序号（1 开始），用于匹配 LLM 返回的 index
     */
    private String resolveRawContent(
            SourceDocumentEntity source, int sourceIndex, Map<Integer, String> expandedContents) {
        String expandedContent = expandedContents.get(sourceIndex);
        if (expandedContent != null && !expandedContent.isBlank()) {
            return expandedContent;
        }
        if (source.getSnippet() != null && !source.getSnippet().isBlank()) {
            return source.getSnippet();
        }
        return "该资料围绕研究目标提供了基础说明，具体内容有待真实抓取补充。";
    }
}
