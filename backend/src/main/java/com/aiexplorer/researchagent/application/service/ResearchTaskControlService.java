package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.HumanConfirmationEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.HumanConfirmationRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchPlanRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.shared.annotation.AuditLog; // 审计注解（场景⑤：贴了它的方法自动留痕）
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
    @AuditLog("CONFIRM_PLAN") // 场景⑤：审计留痕——确认/拒绝计划
    @Transactional
    public void confirmPlan(UUID taskId, boolean approved, String responseMessage) {
        ResearchTaskEntity task = requireTask(taskId);
        requireStatus(task, "确认计划", TaskStatus.WAITING_FOR_CONFIRMATION); // 状态机守卫：只有"待确认"的任务能被确认
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
    @AuditLog("PAUSE_TASK") // 场景⑤：审计留痕——暂停
    @Transactional
    public void pauseTask(UUID taskId) {
        ResearchTaskEntity task = requireTask(taskId);
        if (task.getStatus() == TaskStatus.PAUSED) {
            return; // 幂等：目标状态已达成，重复请求无害（直接按成功返回）
        }
        requireNotTerminal(task, "暂停"); // 状态机守卫：已结束的任务不能被"暂停"
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
    @AuditLog("RESUME_TASK") // 场景⑤：审计留痕——恢复
    @Transactional
    public void resumeTask(UUID taskId) {
        ResearchTaskEntity task = requireTask(taskId);
        // 严格守卫：只允许从 PAUSED 恢复（防止对运行中的任务重复触发执行器）
        requireStatus(task, "恢复执行", TaskStatus.PAUSED);
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
    @AuditLog("CANCEL_TASK") // 场景⑤：审计留痕——取消
    @Transactional
    public void cancelTask(UUID taskId) {
        ResearchTaskEntity task = requireTask(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return; // 幂等：已取消，重复请求无害
        }
        requireNotTerminal(task, "取消"); // 状态机守卫：已完成/已失败的任务不能被"取消"
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

    /**
     * 状态机守卫（严格版）：仅当任务处于指定状态时放行，否则拒绝。
     * 拒绝 → IllegalStateException → 值班室转 400 INVALID_STATE（"任务当前状态为 X，无法 Y"）。
     */
    private void requireStatus(ResearchTaskEntity task, String action, TaskStatus expected) {
        if (task.getStatus() != expected) {
            throw new IllegalStateException(
                    "任务当前状态为 " + task.getStatus() + "，无法" + action);
        }
    }

    /**
     * 状态机守卫（终态版）：已完成/已失败/已取消的任务（终态）拒绝任何迁移。
     */
    private void requireNotTerminal(ResearchTaskEntity task, String action) {
        if (task.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "任务已结束（" + task.getStatus() + "），无法" + action);
        }
    }
}
