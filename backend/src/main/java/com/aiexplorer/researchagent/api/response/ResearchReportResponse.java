package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ReportStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 表示研究报告接口返回的结构化数据。
 */
public record ResearchReportResponse(
        UUID id,
        UUID taskId,
        String summary,
        JsonNode keyFindings,
        String finalRecommendation,
        String reportMarkdown,
        ReportStatus status,
        OffsetDateTime generatedAt,
        OffsetDateTime updatedAt) {
}
