package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 表示任务列表场景下使用的摘要信息。
 */
@Schema(description = "任务摘要（列表项）")
public record TaskSummaryResponse(
        @Schema(description = "任务主键（UUID）", example = "f84bca74-13e1-4dd7-ac11-74c6fe4434a0")
        UUID id,

        @Schema(description = "业务任务号（TASK- 前缀，便于人工沟通）", example = "TASK-12345678")
        String taskNo,

        @Schema(description = "任务标题", example = "调研 Java Agent 架构")
        String title,

        @Schema(description = "执行模式：SYNC 同步 / ASYNC 异步（Kafka）", example = "ASYNC")
        ExecutionMode executionMode,

        @Schema(description = "任务状态：WAITING_FOR_CONFIRMATION / RUNNING / PAUSED / COMPLETED / CANCELLED / FAILED",
                example = "RUNNING")
        TaskStatus status,

        @Schema(description = "当前阶段：PLANNING / EXECUTING / REPORTING", example = "EXECUTING")
        TaskStage currentStage,

        @Schema(description = "是否需要人工确认计划", example = "true")
        boolean requiresConfirmation,

        @Schema(description = "创建时间（ISO 8601，含时区）", example = "2026-10-25T17:30:00+08:00")
        OffsetDateTime createdAt,

        @Schema(description = "最后更新时间（ISO 8601，含时区）", example = "2026-10-25T17:35:12+08:00")
        OffsetDateTime updatedAt) {
}
