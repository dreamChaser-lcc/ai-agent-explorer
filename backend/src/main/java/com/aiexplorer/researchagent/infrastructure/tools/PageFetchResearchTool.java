package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 提供第一版的页面抓取占位实现，基于搜索结果生成正文内容。
 */
@Component
public class PageFetchResearchTool implements ResearchTool {

    private final ObjectMapper objectMapper;

    public PageFetchResearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.FETCH;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        List<ToolSourceDocument> fetchedSources = context.existingSources().stream()
                .filter(source -> source.getSourceType() == SourceType.SEARCH_RESULT)
                .limit(3)
                .map(this::toFetchedDocument)
                .toList();

        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "fetchedCount", fetchedSources.size(),
                        "message", "已抓取候选来源正文"
                )),
                fetchedSources
        );
    }

    /**
     * 将搜索结果转换为带正文的抓取结果。
     */
    private ToolSourceDocument toFetchedDocument(SourceDocumentEntity source) {
        String rawContent = """
                该资料围绕 Research Agent 的执行链进行了说明，重点包括：
                1. 先生成研究计划并等待确认；
                2. 再执行搜索、抓取、总结和引用提取；
                3. 最终输出带证据来源的研究报告。
                在 Java 技术栈下，Spring Boot 适合作为后端骨架，SSE 适合承载单向进度推送。
                """.trim();

        return new ToolSourceDocument(
                SourceType.FETCHED_PAGE,
                source.getUrl(),
                source.getDomain() != null ? source.getDomain() : URI.create(source.getUrl()).getHost(),
                source.getTitle(),
                source.getSnippet(),
                rawContent,
                Integer.toHexString((source.getUrl() + rawContent).hashCode()),
                "zh-CN",
                FetchStatus.SUCCESS,
                source.getRelevanceScore(),
                false,
                objectMapper.valueToTree(Map.of(
                        "originSourceId", source.getId(),
                        "provider", "mock-page-fetch"
                ))
        );
    }
}
