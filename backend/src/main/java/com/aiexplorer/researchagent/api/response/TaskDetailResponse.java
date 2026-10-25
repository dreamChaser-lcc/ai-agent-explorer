package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 表示任务详情接口返回的完整视图数据。
 */
@Schema(description = "任务详情（含最新计划摘要与步骤列表）")
public record TaskDetailResponse(
        @Schema(description = "任务主键（UUID）", example = "f84bca74-13e1-4dd7-ac11-74c6fe4434a0")
        UUID id,

        @Schema(description = "业务任务号", example = "TASK-12345678")
        String taskNo,

        @Schema(description = "任务标题", example = "调研 Java Agent 架构")
        String title,

        @Schema(description = "研究目标（原始输入）", example = "梳理可落地的 DDD 切入点")
        String goal,

        @Schema(description = "执行模式：SYNC 同步 / ASYNC 异步（Kafka）", example = "ASYNC")
        ExecutionMode executionMode,

        @Schema(description = "任务状态", example = "WAITING_FOR_CONFIRMATION")
        TaskStatus status,

        @Schema(description = "当前阶段", example = "PLANNING")
        TaskStage currentStage,

        @Schema(description = "是否需要人工确认计划", example = "true")
        boolean requiresConfirmation,

        @Schema(description = "最新计划的摘要（未生成计划时为 null）", example = "等待确认的研究计划")
        String latestPlanSummary,

        @Schema(description = "计划中的步骤标题列表（按执行顺序）",
                example = "[\"搜索候选资料\", \"抓取资料正文\"]")
        List<String> plannedSteps,

        @Schema(description = "创建时间（ISO 8601，含时区）", example = "2026-10-25T17:30:00+08:00")
        OffsetDateTime createdAt,

        @Schema(description = "最后更新时间（ISO 8601，含时区）", example = "2026-10-25T17:35:12+08:00")
        OffsetDateTime updatedAt) {
}
