package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 表示任务详情接口返回的完整视图数据。
 */
public record TaskDetailResponse(
        UUID id,
        String taskNo,
        String title,
        String goal,
        ExecutionMode executionMode,
        TaskStatus status,
        TaskStage currentStage,
        boolean requiresConfirmation,
        String latestPlanSummary,
        List<String> plannedSteps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
