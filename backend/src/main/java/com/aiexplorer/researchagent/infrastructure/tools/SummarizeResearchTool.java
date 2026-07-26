package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 提供第一版的总结工具占位实现。
 */
@Component
public class SummarizeResearchTool implements ResearchTool {

    private final ObjectMapper objectMapper;

    public SummarizeResearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.SUMMARIZE;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        List<String> findings = context.existingSources().stream()
                .filter(source -> source.getSourceType() == SourceType.FETCHED_PAGE)
                .map(source -> source.getTitle() + "：适合用于构建有状态、可观测的 Research Agent。")
                .limit(3)
                .toList();

        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "summary", "现有资料共同指向一个结论：Research Agent 第一版应优先保证可控执行链，而不是过度追求自治。",
                        "findings", findings,
                        "sourceCount", findings.size()
                )),
                List.of()
        );
    }
}
