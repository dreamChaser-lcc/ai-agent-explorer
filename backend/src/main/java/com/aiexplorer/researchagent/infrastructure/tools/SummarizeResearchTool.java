package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.infrastructure.llm.LlmContentGenerator;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 总结工具（场景⑧延伸：从占位实现升级为真实 LLM 归纳）。
 *
 * 执行策略（可用性优先，两级）：
 *   ① 有已抓取资料 → 调 LLM 生成结构化总结（复用 LlmCallGuard 的超时/重试/降级）；
 *   ② 无资料 / LLM 不可用 / 返回不可解析 → 降级为固定文案（保证步骤永远能完成，不阻塞任务）。
 *
 * 产出结构保持不变（summary / findings / sourceCount），新增 generator 字段标明来源，
 * 下游 ResearchReportAssemblyService 无需改协议即可消费。
 */
@Component
public class SummarizeResearchTool implements ResearchTool {

    private static final Logger log = LoggerFactory.getLogger(SummarizeResearchTool.class);

    /** 送给 LLM 的资料条数上限（控制 token 成本）。 */
    private static final int MAX_SOURCE_COUNT = 5;

    /** 每条资料正文截断长度（字符）。 */
    private static final int MAX_SNIPPET_LENGTH = 240;

    private final ObjectMapper objectMapper;
    private final LlmContentGenerator llmContentGenerator;

    public SummarizeResearchTool(ObjectMapper objectMapper, LlmContentGenerator llmContentGenerator) {
        this.objectMapper = objectMapper;
        this.llmContentGenerator = llmContentGenerator;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.SUMMARIZE;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        List<SourceDocumentEntity> fetchedSources = context.existingSources().stream()
                .filter(source -> source.getSourceType() == SourceType.FETCHED_PAGE)
                .limit(MAX_SOURCE_COUNT)
                .toList();

        // ① 优先：LLM 归纳（无资料时不调用，避免模型凭空编造）
        if (!fetchedSources.isEmpty()) {
            String promptText = buildSummarizePrompt(context.task().getGoal(), fetchedSources);
            Optional<SummarizeDraft> draftOptional = llmContentGenerator
                    .generateJson(promptText)
                    .flatMap(this::parseDraft);
            if (draftOptional.isPresent()) {
                SummarizeDraft draft = draftOptional.get();
                log.info("总结步骤：LLM 归纳成功，资料 {} 条，要点 {} 个",
                        fetchedSources.size(), draft.findings().size());
                return new ResearchToolResult(
                        objectMapper.valueToTree(Map.of(
                                "summary", draft.summary(),
                                "findings", draft.findings(),
                                "sourceCount", fetchedSources.size(),
                                "generator", "llm"
                        )),
                        List.of());
            }
            log.warn("总结步骤：LLM 不可用或返回不可解析，降级为固定文案");
        }

        // ② 降级：原有固定文案（保留原逻辑作为兜底，保证流程不中断）
        List<String> fallbackFindings = fetchedSources.stream()
                .map(source -> source.getTitle() + "：适合用于构建有状态、可观测的 Research Agent。")
                .toList();
        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "summary", "现有资料共同指向一个结论：Research Agent 第一版应优先保证可控执行链，而不是过度追求自治。",
                        "findings", fallbackFindings,
                        "sourceCount", fallbackFindings.size(),
                        "generator", "fallback"
                )),
                List.of()
        );
    }

    /**
     * 组装总结提示词：研究目标 + 资料清单（标题/URL/片段）。
     *
     * 三个约束：只输出 JSON（便于解析）、要点必须基于资料（防编造）、中文。
     */
    private String buildSummarizePrompt(String researchGoal, List<SourceDocumentEntity> sources) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是研究助理。请基于下列资料，围绕研究目标做归纳总结。\n\n");
        promptBuilder.append("研究目标：").append(researchGoal == null ? "" : researchGoal).append("\n\n");
        promptBuilder.append("资料清单：\n");
        for (int index = 0; index < sources.size(); index++) {
            SourceDocumentEntity source = sources.get(index);
            promptBuilder.append(index + 1).append(". ")
                    .append(safeText(source.getTitle(), "（无标题）"))
                    .append(" | ")
                    .append(safeText(source.getUrl(), "（无链接）"))
                    .append("\n");
            String snippet = firstNonBlank(source.getSnippet(), source.getRawContent());
            if (snippet != null) {
                promptBuilder.append("   片段：").append(truncate(snippet, MAX_SNIPPET_LENGTH)).append("\n");
            }
        }
        promptBuilder.append("""

                输出要求（严格遵守）：
                1. 只输出一个 JSON 对象，不要解释文字，不要 markdown 代码块；
                2. 结构：{"summary":"150 字以内的总体结论","findings":["要点1","要点2","要点3"]}
                3. findings 输出 3-5 条，每条一句话，必须基于给定资料，不要编造资料中不存在的内容；
                4. 全部使用中文。
                """);
        return promptBuilder.toString();
    }

    /** 解析 LLM 返回的总结 JSON；结构不合法返回 empty（交给降级）。 */
    private Optional<SummarizeDraft> parseDraft(JsonNode jsonNode) {
        String summaryText = jsonNode.path("summary").asText("");
        if (summaryText.isBlank()) {
            return Optional.empty();
        }
        List<String> findings = new ArrayList<>();
        JsonNode findingsNode = jsonNode.path("findings");
        if (findingsNode.isArray()) {
            findingsNode.forEach(node -> {
                String findingText = node.asText("");
                if (!findingText.isBlank()) {
                    findings.add(findingText);
                }
            });
        }
        return Optional.of(new SummarizeDraft(summaryText, findings));
    }

    private String safeText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /** 总结草稿：LLM 解析后的中间结构。 */
    private record SummarizeDraft(String summary, List<String> findings) {
    }
}
