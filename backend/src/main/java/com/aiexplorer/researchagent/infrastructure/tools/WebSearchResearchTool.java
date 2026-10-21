package com.aiexplorer.researchagent.infrastructure.tools; // 基础设施层 - 工具适配器

import com.aiexplorer.researchagent.infrastructure.search.SearchHit;
import com.aiexplorer.researchagent.infrastructure.search.SearchProvider;
import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepType; // 步骤类型：该工具绑定到 SEARCH
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 序列化
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component; // 标记为 Spring Bean

/**
 * 网页搜索研究工具。
 *
 * 职责：执行 SEARCH 步骤——根据研究目标构造候选搜索来源。
 *
 * 场景⑧延伸（2026-10-19）改造：从"硬编码 3 条示例"升级为"可插拔搜索提供方"：
 *   ① 默认（未接 API）：{@link com.aiexplorer.researchagent.infrastructure.search.LlmSearchProvider}
 *      由 LLM 基于自身知识生成候选资料——保证功能完整；
 *      来源标记为 {@link SourceType#LLM_KNOWLEDGE} + url 伪协议 {@code llm://knowledge/N}，
 *      **绝不伪装成真实网页**（报告会明确标注）。
 *   ② 以后接真实搜索 API（Tavily/Bing/博查/百度/维基百科）：
 *      新建 {@link SearchProvider} 实现 → 在 LlmClientConfiguration 按配置选择 → **本类不用改**；
 *   ③ LLM 失败/无结果 → 回退内置示例数据（保证步骤永远能完成）。
 *
 * 接入方式（新增工具时参考）：
 *   1. 实现 ResearchTool 接口
 *   2. 加 @Component 让 Spring 自动扫描
 *   3. getSupportedStepType() 返回该工具负责的 StepType
 *   4. ResearchToolRegistry 会自动发现并入注册表
 */
@Component // Spring 扫描并注册，构造器会被注入到 ResearchToolRegistry
public class WebSearchResearchTool implements ResearchTool { // 实现工具接口

    private static final Logger log = LoggerFactory.getLogger(WebSearchResearchTool.class);

    private final ObjectMapper objectMapper; // JSON 序列化，Spring 自动注入
    private final SearchProvider searchProvider; // 搜索提供方（默认 LLM 知识，可换真实 API）

    public WebSearchResearchTool(ObjectMapper objectMapper, SearchProvider searchProvider) {
        this.objectMapper = objectMapper;
        this.searchProvider = searchProvider;
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
     * 执行搜索：通过搜索提供方取候选来源；提供方无结果时回退内置示例。
     *
     * @param context 工具执行上下文（含任务信息 + 已有资料来源）
     * @return ResearchToolResult（含 outputPayload 和候选来源列表）
     */
    @Override
    public ResearchToolResult execute(ResearchToolContext context) {
        // 从上下文提取研究目标
        String goal = context.task().getGoal();

        // ① 优先走搜索提供方（默认 LLM 知识检索，未来可换真实 API）
        List<SearchHit> hits = searchProvider.search(goal);
        String providerDescription = searchProvider.describe();
        boolean degraded = hits.isEmpty();

        // ② 提供方无结果 → 回退内置示例（保证功能完整，不阻塞任务）
        if (degraded) {
            log.warn("搜索提供方无结果，回退内置示例资料（provider={}）", providerDescription);
            hits = buildFallbackHits();
            providerDescription = providerDescription + "+fallback";
        }

        List<ToolSourceDocument> sources = hits.stream()
                .map(this::toToolSourceDocument)
                .toList();

        // 组装返回结果
        // ResearchToolResult 是 record（不可变数据类），构造器参数：
        //   参数 1: outputPayload（JSON 输出，存入 step_execution 表）
        //   参数 2: sourceDocuments（来源列表，编排器会持久化到 source_document 表）
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("query", goal);                    // 搜索关键词
        payload.put("resultCount", sources.size());    // 结果数
        payload.put("provider", providerDescription);  // 资料提供方（可追溯）
        payload.put("degraded", degraded);             // 是否走了兜底
        payload.put("message", degraded ? "搜索提供方无结果，已回退内置示例" : "已生成候选来源列表");
        return new ResearchToolResult(objectMapper.valueToTree(payload), sources);
    }

    /**
     * 搜索命中 → 持久化用的来源文档。
     *
     * 关键：根据 url 协议判定来源类型——
     *   llm:// → LLM_KNOWLEDGE（不可溯源，报告需标注）
     *   http(s):// → SEARCH_RESULT（未来真实 API）
     */
    private ToolSourceDocument toToolSourceDocument(SearchHit hit) {
        boolean isLlmKnowledge = hit.url() == null || !hit.url().startsWith("http");
        SourceType sourceType = isLlmKnowledge ? SourceType.LLM_KNOWLEDGE : SourceType.SEARCH_RESULT;
        String domain = isLlmKnowledge ? "llm-knowledge" : URI.create(hit.url()).getHost();

        return new ToolSourceDocument(
                sourceType,                          // 类型：LLM 知识 / 真实搜索结果
                hit.url(),                           // URL（LLM 知识为 llm://knowledge/N）
                domain,                              // 域名
                hit.title(),                         // 标题
                hit.snippet(),                       // 内容要点（搜索阶段先给摘要）
                null,                                // 正文内容（尚未抓取）
                Integer.toHexString((hit.url() + hit.title()).hashCode()), // 内容哈希（去重）
                "zh-CN",                             // 语言
                FetchStatus.PENDING,                 // 待抓取状态
                hit.relevanceScore(),                // 相关性得分
                false,                               // citationReady = false（等后面步骤标记）
                objectMapper.valueToTree(Map.of(
                        "provider", searchProvider.describe() // 元数据：数据提供方
                ))
        );
    }

    /**
     * 内置示例资料（降级兜底）——仅在搜索提供方完全不可用时使用。
     *
     * 这批数据带 example.com 域名，报告里一眼能看出"这是演示数据"。
     */
    private List<SearchHit> buildFallbackHits() {
        return List.of(
                new SearchHit("Java Agent 架构综述",
                        "https://example.com/java-agent-architecture",
                        "围绕 Spring Boot、LangChain4j 和工作流编排的架构资料。", 0.96),
                new SearchHit("Research Agent 模式实践",
                        "https://example.com/research-agent-patterns",
                        "介绍 Research Agent 的规划、抓取、总结和引用链路。", 0.91),
                new SearchHit("SSE 任务进度设计",
                        "https://example.com/sse-progress-design",
                        "关于长任务实时进度流的实现经验。", 0.87)
        );
    }
}
