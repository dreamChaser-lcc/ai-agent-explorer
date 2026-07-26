package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ExecutionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 表示任务步骤执行时间线所需的数据。
 */
public record StepExecutionResponse(
        UUID id,
        UUID stepId,
        int attemptNo,
        String executorType,
        String toolName,
        ExecutionStatus status,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs) {
}
