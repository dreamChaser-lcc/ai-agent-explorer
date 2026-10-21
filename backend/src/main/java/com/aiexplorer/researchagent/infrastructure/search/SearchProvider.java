package com.aiexplorer.researchagent.infrastructure.search;

import java.util.List;

/**
 * 搜索提供方抽象——**预留接真实搜索 API 的扩展点**。
 *
 * 当前实现：
 *   - {@link LlmSearchProvider}：未接 API 时，由 LLM 基于自身知识生成候选资料（默认，保证功能完整）。
 *
 * 以后接真实 API 时（Tavily / Bing / 博查 / 百度千帆 / 维基百科……）：
 *   1. 新建实现类（如 {@code TavilySearchProvider}），实现本接口；
 *   2. 在 {@code LlmClientConfiguration} 里按 {@code app.llm.search-provider} 选择实现；
 *   3. 上层（{@code WebSearchResearchTool}）**一行不用改**——它只依赖本接口。
 *
 * 接口契约：
 *   - 返回的 url 必须能标识来源：真实 API 返回 http(s) 链接；
 *     LLM 知识用 {@code llm://knowledge/xxx} 伪协议，**绝不伪装成真实网页**；
 *   - 失败/无结果返回空列表（由调用方决定降级策略），不抛异常。
 */
public interface SearchProvider {

    /**
     * 围绕查询词检索候选资料。
     *
     * @param query 研究目标（作为查询词）
     * @return 命中的资料列表（可能为空）
     */
    List<SearchHit> search(String query);

    /**
     * 描述当前实现（写入步骤输出与日志，便于回溯"这批资料是谁给的"）。
     */
    String describe();
}
