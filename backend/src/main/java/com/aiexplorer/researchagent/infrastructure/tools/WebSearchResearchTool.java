package com.aiexplorer.researchagent.infrastructure.tools; // 基础设施层 - 工具适配器

import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType; // 步骤类型：该工具绑定到 SEARCH
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 序列化
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component; // 标记为 Spring Bean

/**
 * 网页搜索研究工具（MVP 占位实现）。
 *
 * 职责：执行 SEARCH 步骤——根据研究目标构造候选搜索来源。
 *
 * 当前返回 3 条 hardcoded 模拟数据，后续替换为真实搜索 API。
 *
 * 接入方式（新增工具时参考）：
 *   1. 实现 ResearchTool 接口
 *   2. 加 @Component 让 Spring 自动扫描
 *   3. getSupportedStepType() 返回该工具负责的 StepType
 *   4. ResearchToolRegistry 会自动发现并入注册表
 */
@Component // Spring 扫描并注册，构造器会被注入到 ResearchToolRegistry
public class WebSearchResearchTool implements ResearchTool { // 实现工具接口

    private final ObjectMapper objectMapper; // JSON 序列化，Spring 自动注入

    public WebSearchResearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 声明该工具支持的步骤类型——注册表根据这个建立映射。
     * @return StepType.SEARCH（该工具负责搜索步骤）
     */
    @Override
    public StepType getSupportedStepType() {
        return StepType.SEARCH; // SEARCH 步骤 → 该工具
    }

    /**
     * 执行搜索：构造候选来源列表并返回。
     *
     * @param context 工具执行上下文（含任务信息 + 已有资料来源）
     * @return ResearchToolResult（含 outputPayload 和 3 条 sourceDocuments）
     */
    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        // 从上下文提取研究目标
        String goal = context.task().getGoal();

        // ======= 模拟 3 条候选来源 =======
        List<ToolSourceDocument> sources = List.of(
                buildSource("https://example.com/java-agent-architecture",
                        "Java Agent 架构综述",
                        "围绕 Spring Boot、LangChain4j 和工作流编排的架构资料。",
                        0.96), // 相关性得分，1.0 为完全匹配
                buildSource("https://example.com/research-agent-patterns",
                        "Research Agent 模式实践",
                        "介绍 Research Agent 的规划、抓取、总结和引用链路。",
                        0.91),
                buildSource("https://example.com/sse-progress-design",
                        "SSE 任务进度设计",
                        "关于长任务实时进度流的实现经验。",
                        0.87)
        );

        // 组装返回结果
        // ResearchToolResult 是 record（不可变数据类），构造器参数：
        //   参数 1: outputPayload（JSON 输出，存入 step_execution 表）
        //   参数 2: sourceDocuments（来源列表，编排器会持久化到 source_document 表）
        return new ResearchToolResult(
                objectMapper.valueToTree(Map.of(
                        "query", goal,              // 搜索关键词
                        "resultCount", sources.size(), // 结果数
                        "message", "已生成候选来源列表" // 中文状态提示
                )),
                sources
        );
    }

    /**
     * 构造一条模拟搜索结果来源。
     *
     * ToolSourceDocument 是 record，构造器参数声明如下：
     *   sourceType, url, domain, title, snippet, rawContent,
     *   contentHash, language, fetchStatus, relevanceScore, citationReady, metadata
     *
     * rawContent 为 null 表示尚未抓取，fetchStatus=PENDING 表示等待下一次 FETCH 步骤处理。
     */
    private ToolSourceDocument buildSource(
            String url, String title, String snippet, double relevanceScore) {
        return new ToolSourceDocument(
                SourceType.SEARCH_RESULT,        // 类型：搜索结果
                url,                             // URL
                URI.create(url).getHost(),       // 域名（从 URL 提取）
                title,                           // 页面标题
                snippet,                         // 搜索摘要
                null,                            // 正文内容（尚未抓取）
                Integer.toHexString(url.hashCode()), // 内容哈希 = URL 哈希值（用于去重检测）
                "zh-CN",                        // 语言
                FetchStatus.PENDING,            // 待抓取状态
                relevanceScore,                 // 相关性得分
                false,                          // citationReady = false（等后面步骤标记）
                objectMapper.valueToTree(Map.of(
                        "provider", "mock-search" // 元数据：数据提供方
                ))
        );
    }
}
