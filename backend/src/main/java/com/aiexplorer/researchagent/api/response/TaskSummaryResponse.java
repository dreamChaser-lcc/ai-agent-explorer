package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 表示任务列表场景下使用的摘要信息。
 */
public record TaskSummaryResponse(
        UUID id,
        String taskNo,
        String title,
        ExecutionMode executionMode,
        TaskStatus status,
        TaskStage currentStage,
        boolean requiresConfirmation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
