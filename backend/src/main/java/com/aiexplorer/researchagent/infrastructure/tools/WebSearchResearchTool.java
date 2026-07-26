package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 提供第一版的网页搜索占位实现，先产出可验证的候选来源数据。
 */
@Component
public class WebSearchResearchTool implements ResearchTool {

    private final ObjectMapper objectMapper;

    public WebSearchResearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public StepType getSupportedStepType() {
        return StepType.SEARCH;
    }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        String goal = context.task().getGoal();
        List<ToolSourceDocument> sources = List.of(
                buildSource("https://example.com/java-agent-architecture", "Java Agent 架构综述",
                        "围绕 Spring Boot、LangChain4j 和工作流编排的架构资料。", 0.96),
                buildSource("https://example.com/research-agent-patterns", "Research Agent 模式实践",
                        "介绍 Research Agent 的规划、抓取、总结和引用链路。", 0.91),
                buildSource("https://example.com/sse-progress-design", "SSE 任务进度设计",
                        "关于长任务实时进度流的实现经验。", 0.87)
        );

        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "query", goal,
                        "resultCount", sources.size(),
                        "message", "已生成候选来源列表"
                )),
                sources
        );
    }

    /**
     * 构造一条搜索结果来源。
     */
    private ToolSourceDocument buildSource(String url, String title, String snippet, double relevanceScore) {
        return new ToolSourceDocument(
                SourceType.SEARCH_RESULT,
                url,
                URI.create(url).getHost(),
                title,
                snippet,
                null,
                Integer.toHexString(url.hashCode()),
                "zh-CN",
                FetchStatus.PENDING,
                relevanceScore,
                false,
                objectMapper.valueToTree(Map.of("provider", "mock-search"))
        );
    }
}
