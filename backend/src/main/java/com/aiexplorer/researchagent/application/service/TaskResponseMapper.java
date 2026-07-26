package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.response.ResearchReportResponse;
import com.aiexplorer.researchagent.api.response.StepExecutionResponse;
import com.aiexplorer.researchagent.api.response.TaskEventResponse;
import com.aiexplorer.researchagent.api.response.TaskDetailResponse;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchStepEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.StepExecutionEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 负责将持久化实体转换为接口层响应对象。
 */
@Component
public class TaskResponseMapper {

    /**
     * 将任务实体转换为列表页使用的摘要响应。
     */
    public TaskSummaryResponse toTaskSummary(ResearchTaskEntity task) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getTaskNo(),
                task.getTitle(),
                task.getExecutionMode(),
                task.getStatus(),
                task.getCurrentStage(),
                task.isRequiresConfirmation(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    /**
     * 将任务实体、计划和步骤集合组合为详情响应。
     */
    public TaskDetailResponse toTaskDetail(
            ResearchTaskEntity task,
            ResearchPlanEntity latestPlan,
            List<ResearchStepEntity> steps) {
        List<String> plannedSteps = steps.stream()
                .map(ResearchStepEntity::getTitle)
                .toList();

        return new TaskDetailResponse(
                task.getId(),
                task.getTaskNo(),
                task.getTitle(),
                task.getGoal(),
                task.getExecutionMode(),
                task.getStatus(),
                task.getCurrentStage(),
                task.isRequiresConfirmation(),
                latestPlan != null ? latestPlan.getPlanSummary() : null,
                plannedSteps,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    /**
     * 将步骤执行实体转换为时间线展示对象。
     */
    public StepExecutionResponse toStepExecution(StepExecutionEntity execution) {
        return new StepExecutionResponse(
                execution.getId(),
                execution.getStepId(),
                execution.getAttemptNo(),
                execution.getExecutorType(),
                execution.getToolName(),
                execution.getStatus(),
                execution.getErrorMessage(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getDurationMs()
        );
    }

    /**
     * 将研究报告实体转换为报告响应。
     */
    public ResearchReportResponse toResearchReport(ResearchReportEntity report) {
        return new ResearchReportResponse(
                report.getId(),
                report.getTaskId(),
                report.getSummary(),
                report.getKeyFindings(),
                report.getFinalRecommendation(),
                report.getReportMarkdown(),
                report.getStatus(),
                report.getGeneratedAt(),
                report.getUpdatedAt()
        );
    }

    /**
     * 将任务事件实体转换为事件响应。
     */
    public TaskEventResponse toTaskEvent(TaskEventLogEntity event) {
        return new TaskEventResponse(
                event.getId(),
                event.getTaskId(),
                event.getStepId(),
                event.getEventType(),
                event.getEventMessage(),
                event.getOperatorType(),
                event.getOperatorId(),
                event.getCreatedAt()
        );
    }
}
