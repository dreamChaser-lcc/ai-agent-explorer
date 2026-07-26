package com.aiexplorer.researchagent.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 表示研究工具执行后的结构化输出。
 */
public record ResearchToolResult(
        JsonNode outputPayload,
        List<ToolSourceDocument> sourceDocuments) {
}
