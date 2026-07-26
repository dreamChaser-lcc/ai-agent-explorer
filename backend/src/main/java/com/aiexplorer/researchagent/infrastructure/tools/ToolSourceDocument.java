package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 表示工具执行后需要持久化的一条来源资料。
 */
public record ToolSourceDocument(
        SourceType sourceType,
        String url,
        String domain,
        String title,
        String snippet,
        String rawContent,
        String contentHash,
        String language,
        FetchStatus fetchStatus,
        Double relevanceScore,
        boolean citationReady,
        JsonNode metadata) {
}
