package com.aiexplorer.researchagent.infrastructure.search;

/**
 * 一条搜索命中结果（与具体搜索服务无关的中立结构）。
 *
 * 约定：未接真实搜索 API 时，url 以 {@code llm://knowledge/} 开头，
 * 表示"这是 LLM 基于自身知识生成的资料，不是可访问的真实网页"——
 * 下游据此标记来源可信度，绝不伪装成真实链接。
 */
public record SearchHit(
        String title,
        String url,
        String snippet,
        double relevanceScore) {
}
