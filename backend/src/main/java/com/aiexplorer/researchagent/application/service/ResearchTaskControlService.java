package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.HumanConfirmationEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.HumanConfirmationRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchPlanRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.shared.enums.ConfirmationStatus;
import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.PlanStatus;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责处理研究任务的人工确认与基础控制动作。
 */
@Service
public class ResearchTaskControlService {

    private final ResearchTaskRepository researchTaskRepository;
    private final ResearchPlanRepository researchPlanRepository;
    private final HumanConfirmationRepository humanConfirmationRepository;
    private final TaskEventService taskEventService;
    private final TaskProgressStreamService taskProgressStreamService;
    private final ResearchTaskOrchestrator researchTaskOrchestrator;

    public ResearchTaskControlService(
            ResearchTaskRepository researchTaskRepository,
            ResearchPlanRepository researchPlanRepository,
            HumanConfirmationRepository humanConfirmationRepository,
            TaskEventService taskEventService,
            TaskProgressStreamService taskProgressStreamService,
            ResearchTaskOrchestrator researchTaskOrchestrator) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchPlanRepository = researchPlanRepository;
        this.humanConfirmationRepository = humanConfirmationRepository;
        this.taskEventService = taskEventService;
        this.taskProgressStreamService = taskProgressStreamService;
        this.researchTaskOrchestrator = researchTaskOrchestrator;
    }

    /**
     * 处理研究计划确认结果，并决定任务是继续执行还是终止。
     */
    @Transactional
    public void confirmPlan(UUID taskId, boolean approved, String responseMessage) {
        ResearchTaskEntity task = requireTask(taskId);
        ResearchPlanEntity latestPlan = researchPlanRepository.findByTaskIdOrderByVersionDesc(taskId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("任务缺少可确认的研究计划"));

        HumanConfirmationEntity confirmation = humanConfirmationRepository.findByTaskIdOrderByRequestedAtDesc(taskId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("任务缺少可确认的人工确认记录"));

        confirmation.setResponseMessage(responseMessage);
        confirmation.setRespondedAt(OffsetDateTime.now());
        confirmation.setRespondedBy("user");

        if (approved) {
            latestPlan.setStatus(PlanStatus.CONFIRMED);
            latestPlan.setConfirmationStatus(ConfirmationStatus.APPROVED);
            latestPlan.setConfirmedAt(OffsetDateTime.now());
            confirmation.setStatus(ConfirmationStatus.APPROVED);
            task.setStatus(TaskStatus.RUNNING);
            task.setCurrentStage(TaskStage.EXECUTING);
            task.setRequiresConfirmation(false);
            taskEventService.recordUserEvent(taskId, null, EventType.PLAN_CONFIRMED, "用户已确认研究计划，任务进入执行阶段。");
            taskProgressStreamService.publish(taskId, "task-status", java.util.Map.of(
                    "taskId", taskId,
                    "status", TaskStatus.RUNNING.name(),
                    "stage", TaskStage.EXECUTING.name(),
                    "message", "研究计划已确认，任务开始执行"
            ));
        } else {
            latestPlan.setStatus(PlanStatus.REJECTED);
            latestPlan.setConfirmationStatus(ConfirmationStatus.REJECTED);
            confirmation.setStatus(ConfirmationStatus.REJECTED);
            task.setStatus(TaskStatus.CANCELLED);
            task.setRequiresConfirmation(false);
            task.setErrorMessage(responseMessage);
            taskEventService.recordUserEvent(taskId, null, EventType.PLAN_REJECTED, "用户拒绝研究计划，任务停止执行。");
            taskProgressStreamService.publish(taskId, "task-status", java.util.Map.of(
                    "taskId", taskId,
                    "status", TaskStatus.CANCELLED.name(),
                    "message", "研究计划被拒绝，任务已取消"
            ));
        }

        researchPlanRepository.save(latestPlan);
        humanConfirmationRepository.save(confirmation);
        researchTaskRepository.save(task);

        // 只有计划被批准时才真正进入执行器。
        if (approved) {
            researchTaskOrchestrator.startExecution(taskId);
        }
    }

    /**
     * 暂停运行中的任务，新的步骤不会继续推进。
     */
    @Transactional
    public void pauseTask(UUID taskId) {
        ResearchTaskEntity task = requireTask(taskId);
        task.setStatus(TaskStatus.PAUSED);
        researchTaskRepository.save(task);
        taskEventService.recordUserEvent(taskId, null, EventType.TASK_PAUSED, "任务已暂停。");
        taskProgressStreamService.publish(taskId, "task-status", java.util.Map.of(
                "taskId", taskId,
                "status", TaskStatus.PAUSED.name(),
                "message", "任务已暂停"
        ));
    }

    /**
     * 恢复已暂停任务，并从当前检查点继续执行。
     */
    @Transactional
    public void resumeTask(UUID taskId) {
        ResearchTaskEntity task = requireTask(taskId);
        task.setStatus(TaskStatus.RUNNING);
        task.setCurrentStage(TaskStage.EXECUTING);
        researchTaskRepository.save(task);
        taskEventService.recordUserEvent(taskId, null, EventType.TASK_RESUMED, "任务已恢复执行。");
        taskProgressStreamService.publish(taskId, "task-status", java.util.Map.of(
                "taskId", taskId,
                "status", TaskStatus.RUNNING.name(),
                "stage", TaskStage.EXECUTING.name(),
                "message", "任务已恢复执行"
        ));
        researchTaskOrchestrator.startExecution(taskId);
    }

    /**
     * 取消任务并广播状态变更。
     */
    @Transactional
    public void cancelTask(UUID taskId) {
        ResearchTaskEntity task = requireTask(taskId);
        task.setStatus(TaskStatus.CANCELLED);
        researchTaskRepository.save(task);
        taskEventService.recordUserEvent(taskId, null, EventType.TASK_CANCELLED, "任务已取消。");
        taskProgressStreamService.publish(taskId, "task-status", java.util.Map.of(
                "taskId", taskId,
                "status", TaskStatus.CANCELLED.name(),
                "message", "任务已取消"
        ));
    }

    /**
     * 加载任务，不存在时抛出统一异常。
     */
    private ResearchTaskEntity requireTask(UUID taskId) {
        return researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}
