package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 提供第一版的引用提取占位实现。
 */
@Component
public class CitationExtractResearchTool implements ResearchTool {

    private final ObjectMapper objectMapper;

    public CitationExtractResearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.CITATION_EXTRACT;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        List<Map<String, Object>> citations = context.existingSources().stream()
                .filter(source -> source.getSourceType() == SourceType.FETCHED_PAGE)
                .limit(3)
                .map(source -> Map.<String, Object>of(
                        "title", source.getTitle(),
                        "url", source.getUrl(),
                        "snippet", source.getSnippet()
                ))
                .toList();

        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "citationCount", citations.size(),
                        "citations", citations
                )),
                List.of()
        );
    }
}
