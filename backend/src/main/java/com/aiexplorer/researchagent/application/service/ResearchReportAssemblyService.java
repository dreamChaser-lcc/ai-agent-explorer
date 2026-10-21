package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.llm.LlmContentGenerator;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.StepExecutionEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchReportRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.SourceDocumentRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.StepExecutionRepository;
import com.aiexplorer.researchagent.shared.enums.ReportStatus;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责根据步骤执行结果和来源资料组装研究报告。
 *
 * 场景⑧延伸（2026-10-19）：报告核心内容从"固定拼接"升级为"LLM 撰写 + 失败降级"：
 *   - 摘要：来自总结步骤（该步骤本身已改为 LLM 归纳）；
 *   - 关键发现 / 行动建议：由 LLM 基于资料撰写；
 *   - LLM 不可用 → 回退原有拼接逻辑（报告仍能生成，质量退化但不阻塞）。
 */
@Service
public class ResearchReportAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(ResearchReportAssemblyService.class);

    /** 送给 LLM 的资料条数上限（控制 token）。 */
    private static final int MAX_SOURCE_COUNT = 5;

    /** 每条资料正文截断长度（字符）。 */
    private static final int MAX_SNIPPET_LENGTH = 200;

    private final ResearchReportRepository researchReportRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ResearchTaskRepository researchTaskRepository;
    private final LlmContentGenerator llmContentGenerator;
    private final ObjectMapper objectMapper;

    public ResearchReportAssemblyService(
            ResearchReportRepository researchReportRepository,
            SourceDocumentRepository sourceDocumentRepository,
            StepExecutionRepository stepExecutionRepository,
            ResearchTaskRepository researchTaskRepository,
            LlmContentGenerator llmContentGenerator,
            ObjectMapper objectMapper) {
        this.researchReportRepository = researchReportRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.researchTaskRepository = researchTaskRepository;
        this.llmContentGenerator = llmContentGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据任务执行结果和来源资料生成最终研究报告。
     */
    @CacheEvict(value = "report", key = "#taskId") // ★ 场景⑦：写路径失效——报告重建后踢掉旧缓存（下次读自动重建）
    @Transactional
    public ResearchReportEntity buildReport(UUID taskId) {
        List<SourceDocumentEntity> sources = sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        List<StepExecutionEntity> executions = stepExecutionRepository.findByTaskIdOrderByStartedAtDesc(taskId);

        JsonNode summarizeOutput = findExecutionOutput(executions, StepType.SUMMARIZE.name());
        JsonNode citationOutput = findExecutionOutput(executions, StepType.CITATION_EXTRACT.name());

        // ★ 兜底（2026-10-20 实测发现）：LLM 生成的计划可能不含 CITATION_EXTRACT 步骤
        //   （本次实测：两个 SEARCH + FETCH + SUMMARIZE + REPORT，引用工具从未被调用）。
        //   此时直接从来源正文取首句作为引文，保证报告一定有引文块。
        if (citationOutput == null || citationOutput.path("citations").isEmpty()) {
            JsonNode fallbackCitations = buildFallbackCitationsFromSources(sources);
            if (!fallbackCitations.path("citations").isEmpty()) {
                citationOutput = fallbackCitations;
                log.info("报告生成：计划未包含引用步骤，已从来源正文兜底生成引文 {} 条",
                        fallbackCitations.path("citations").size());
            }
        }

        String summary = summarizeOutput != null && summarizeOutput.has("summary")
                ? summarizeOutput.get("summary").asText()
                : "当前研究任务已经完成基础资料收集与整理。";

        // ★ 场景⑧延伸：关键发现与建议优先由 LLM 撰写（无资料时不调用，避免凭空编造）
        String researchGoal = researchTaskRepository.findById(taskId)
                .map(ResearchTaskEntity::getGoal)
                .orElse("");
        Optional<ReportDraft> draftOptional = sources.isEmpty()
                ? Optional.empty()
                : llmContentGenerator
                        .generateJson(buildReportPrompt(researchGoal, summary, sources))
                        .flatMap(this::parseReportDraft);

        List<String> keyFindingList = draftOptional.map(ReportDraft::keyFindings).orElse(List.of());
        String recommendation = draftOptional.map(ReportDraft::recommendation)
                .orElse("建议继续沿用 Spring Boot + SSE + 分步骤可控执行链的方向推进 MVP。");
        if (draftOptional.isPresent()) {
            log.info("报告生成：LLM 撰写成功，关键发现 {} 条", keyFindingList.size());
        } else {
            log.warn("报告生成：LLM 不可用或返回不可解析，降级为拼接逻辑");
        }

        Map<String, Object> keyFindings = new LinkedHashMap<>();
        keyFindings.put("findings", keyFindingList);
        keyFindings.put("summaryFindings", summarizeOutput);
        keyFindings.put("citations", citationOutput);
        keyFindings.put("sourceCount", sources.size());
        keyFindings.put("generator", draftOptional.isPresent() ? "llm" : "assembly");

        String markdown = buildMarkdown(summary, keyFindingList, sources, recommendation, citationOutput);

        ResearchReportEntity report = researchReportRepository.findByTaskId(taskId)
                .orElseGet(ResearchReportEntity::new);
        report.setTaskId(taskId);
        report.setVersion(1);
        report.setSummary(summary);
        report.setKeyFindings(objectMapper.valueToTree(keyFindings));
        report.setFinalRecommendation(recommendation);
        report.setReportMarkdown(markdown);
        report.setReportJson(objectMapper.valueToTree(Map.of(
                "summary", summary,
                "recommendation", recommendation,
                "generator", draftOptional.isPresent() ? "llm" : "assembly",
                "sources", distinctSourceUrls(sources)
        )));
        report.setStatus(ReportStatus.FINAL);
        report.setGeneratedAt(OffsetDateTime.now());
        return researchReportRepository.save(report);
    }

    /**
     * 从执行记录中找到指定步骤类型对应的输出结果。
     */
    private JsonNode findExecutionOutput(List<StepExecutionEntity> executions, String stepTypeName) {
        return executions.stream()
                .map(StepExecutionEntity::getOutputPayload)
                .filter(payload -> payload != null && payload.has("stepType"))
                .filter(payload -> stepTypeName.equals(payload.get("stepType").asText()))
                .map(payload -> payload.has("result") ? payload.get("result") : payload)
                .findFirst()
                .orElse(null);
    }

    /**
     * 组装报告生成提示词：研究目标 + 已有总结 + 资料来源 + 输出格式约束。
     */
    private String buildReportPrompt(String researchGoal, String summary, List<SourceDocumentEntity> sources) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是研究报告撰写者。请基于下列材料，撰写研究报告的核心内容。\n\n");
        promptBuilder.append("研究目标：").append(researchGoal == null ? "" : researchGoal).append("\n\n");
        promptBuilder.append("已有总结：").append(summary == null ? "" : summary).append("\n\n");
        promptBuilder.append("资料来源：\n");
        List<SourceDocumentEntity> limitedSources = sources.stream().limit(MAX_SOURCE_COUNT).toList();
        for (int index = 0; index < limitedSources.size(); index++) {
            SourceDocumentEntity source = limitedSources.get(index);
            promptBuilder.append(index + 1).append(". ")
                    .append(source.getTitle() == null ? "（无标题）" : source.getTitle())
                    .append(" | ")
                    .append(source.getUrl() == null ? "（无链接）" : source.getUrl())
                    .append("\n");
            String snippet = firstNonBlank(source.getSnippet(), source.getRawContent());
            if (snippet != null) {
                promptBuilder.append("   片段：").append(truncate(snippet, MAX_SNIPPET_LENGTH)).append("\n");
            }
        }
        promptBuilder.append("""

                输出要求（严格遵守）：
                1. 只输出一个 JSON 对象，不要解释文字，不要 markdown 代码块；
                2. 结构：{"keyFindings":["发现1","发现2","发现3"],"recommendation":"100 字以内的行动建议"}
                3. keyFindings 输出 3-5 条，每条一句话，必须基于给定材料，不要编造；
                4. 全部使用中文。
                """);
        return promptBuilder.toString();
    }

    /** 解析 LLM 返回的报告 JSON；结构不合法返回 empty（交给降级）。 */
    private Optional<ReportDraft> parseReportDraft(JsonNode jsonNode) {
        String recommendationText = jsonNode.path("recommendation").asText("");
        if (recommendationText.isBlank()) {
            return Optional.empty();
        }
        List<String> keyFindingList = new ArrayList<>();
        JsonNode findingsNode = jsonNode.path("keyFindings");
        if (findingsNode.isArray()) {
            findingsNode.forEach(node -> {
                String findingText = node.asText("");
                if (!findingText.isBlank()) {
                    keyFindingList.add(findingText);
                }
            });
        }
        return Optional.of(new ReportDraft(keyFindingList, recommendationText));
    }

    /**
     * 生成报告的 Markdown 视图。
     *
     * 场景⑧延伸：新增「关键发现」小节（内容来自 LLM），结构更接近真实研究报告。
     * 2026-10-20：参考来源渲染**逐字引文**（引用块），引用提取步骤升级后报告更像真报告。
     */
    private String buildMarkdown(
            String summary, List<String> keyFindingList,
            List<SourceDocumentEntity> sources, String recommendation, JsonNode citationOutput) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 研究报告\n\n");
        builder.append("## 摘要\n").append(summary).append("\n\n");
        if (!keyFindingList.isEmpty()) {
            builder.append("## 关键发现\n");
            keyFindingList.forEach(finding -> builder.append("- ").append(finding).append("\n"));
            builder.append("\n");
        }
        builder.append("## 建议\n").append(recommendation).append("\n\n");
        builder.append("## 参考来源\n");

        // 优先使用引用提取步骤的产出（含逐字引文）；为空则回退资料列表
        JsonNode citationsNode = citationOutput == null ? null : citationOutput.path("citations");
        if (citationsNode != null && citationsNode.isArray() && !citationsNode.isEmpty()) {
            java.util.Set<String> seenCitationUrls = new java.util.LinkedHashSet<>();
            citationsNode.forEach(citation -> {
                String citationUrl = citation.path("url").asText("");
                if (!citationUrl.isBlank() && !seenCitationUrls.add(citationUrl)) {
                    return; // 同一来源只展示一次
                }
                builder.append("- ").append(citation.path("title").asText("（无标题）"));
                if (citationUrl.startsWith("llm://")) {
                    builder.append("（LLM 知识，未接真实搜索，不可溯源）");
                } else if (!citationUrl.isBlank()) {
                    builder.append(" - ").append(citationUrl);
                }
                builder.append("\n");
                String quote = citation.path("quote").asText("");
                if (!quote.isBlank()) {
                    builder.append("  > \"").append(quote).append("\"\n");
                }
            });
        } else {
            // 兜底：资料列表（按 URL 去重）
            java.util.Set<String> seenUrls = new java.util.LinkedHashSet<>();
            sources.stream()
                    .filter(source -> source.getUrl() != null && seenUrls.add(source.getUrl()))
                    .limit(5)
                    .forEach(source -> {
                        builder.append("- ").append(source.getTitle());
                        String sourceUrl = source.getUrl();
                        if (sourceUrl != null && sourceUrl.startsWith("llm://")) {
                            // 场景⑧延伸：LLM 知识来源明确标注不可溯源，绝不伪装成真实网页
                            builder.append("（LLM 知识，未接真实搜索，不可溯源）");
                        } else {
                            builder.append(" - ").append(sourceUrl);
                        }
                        builder.append("\n");
                    });
        }
        return builder.toString();
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

    /**
     * 从来源正文兜底生成引文（计划里没有引用步骤时使用）。
     *
     * 取每篇正文的第一句（按句号/问号/感叹号/换行切分），标注 quoteSource=first-sentence-fallback，
     * 与 LLM 摘句（quoteSource=llm-extracted）区分开——**不冒充"智能提取"**。
     */
    private JsonNode buildFallbackCitationsFromSources(List<SourceDocumentEntity> sources) {
        java.util.Set<String> seenUrls = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (SourceDocumentEntity source : sources) {
            if (citations.size() >= 3) {
                break;
            }
            String sourceUrl = source.getUrl();
            if (sourceUrl == null || !seenUrls.add(sourceUrl)) {
                continue;
            }
            String rawContent = source.getRawContent() != null && !source.getRawContent().isBlank()
                    ? source.getRawContent() : source.getSnippet();
            String quote = rawContent == null ? "" : rawContent.split("[。！？\\n]")[0].trim();
            if (quote.length() > 90) {
                quote = quote.substring(0, 90) + "...";
            }
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("title", source.getTitle());
            citation.put("url", sourceUrl);
            citation.put("quote", quote);
            citation.put("quoteSource", "first-sentence-fallback");
            citations.add(citation);
        }
        return objectMapper.valueToTree(Map.of(
                "citationCount", citations.size(),
                "citations", citations,
                "generator", "fallback-from-sources"));
    }

    /**
     * 提取去重后的来源 URL（最多 5 条）。
     *
     * SEARCH 与 FETCH 两个步骤会各插一条来源记录，不去重会出现重复链接。
     */
    private List<String> distinctSourceUrls(List<SourceDocumentEntity> sources) {
        java.util.Set<String> seenUrls = new java.util.LinkedHashSet<>();
        List<String> urls = new ArrayList<>();
        for (SourceDocumentEntity source : sources) {
            if (source.getUrl() != null && seenUrls.add(source.getUrl())) {
                urls.add(source.getUrl());
                if (urls.size() >= 5) {
                    break;
                }
            }
        }
        return urls;
    }

    /** 报告草稿：LLM 解析后的中间结构。 */
    private record ReportDraft(List<String> keyFindings, String recommendation) {
    }
}
