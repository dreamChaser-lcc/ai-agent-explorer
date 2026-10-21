package com.aiexplorer.researchagent.shared.enums;

/**
 * 表示资料来源进入系统的方式。
 */
public enum SourceType {
    SEARCH_RESULT,
    FETCHED_PAGE,
    MANUAL_INPUT,
    /** 场景⑧延伸：未接搜索 API 时，由 LLM 基于自身知识生成的资料（不代表真实网页，不可溯源）。 */
    LLM_KNOWLEDGE
}
