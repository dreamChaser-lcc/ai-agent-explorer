package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchStepEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.StepExecutionEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.SourceDocumentRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.StepExecutionRepository;
import com.aiexplorer.researchagent.infrastructure.tools.ResearchToolContext;
import com.aiexplorer.researchagent.infrastructure.tools.ResearchToolResult;
import com.aiexplorer.researchagent.infrastructure.tools.ToolSourceDocument;
import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.ExecutionStatus;
import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepStatus;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 负责驱动研究任务按步骤执行，并统一维护执行状态。
 */
@Service
public class ResearchTaskOrchestrator {

    private final ResearchTaskRepository researchTaskRepository;
    private final ResearchStepRepository researchStepRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ResearchToolRegistry researchToolRegistry;
    private final ResearchReportAssemblyService researchReportAssemblyService;
    private final TaskEventService taskEventService;
    private final TaskProgressStreamService taskProgressStreamService;
    private final ObjectMapper objectMapper;
    private final Executor researchTaskExecutor;

    public ResearchTaskOrchestrator(
            ResearchTaskRepository researchTaskRepository,
            ResearchStepRepository researchStepRepository,
            StepExecutionRepository stepExecutionRepository,
            SourceDocumentRepository sourceDocumentRepository,
            ResearchToolRegistry researchToolRegistry,
            ResearchReportAssemblyService researchReportAssemblyService,
            TaskEventService taskEventService,
            TaskProgressStreamService taskProgressStreamService,
            ObjectMapper objectMapper,
            @Qualifier("researchTaskExecutor") Executor researchTaskExecutor) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchStepRepository = researchStepRepository;
        this.stepExecutionRepository = stepExecutionRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.researchToolRegistry = researchToolRegistry;
        this.researchReportAssemblyService = researchReportAssemblyService;
        this.taskEventService = taskEventService;
        this.taskProgressStreamService = taskProgressStreamService;
        this.objectMapper = objectMapper;
        this.researchTaskExecutor = researchTaskExecutor;
    }

    /**
     * 按任务配置的执行模式启动任务。
     */
    public void startExecution(UUID taskId) {
        ResearchTaskEntity task = loadTask(taskId);
        if (task.getExecutionMode() == ExecutionMode.ASYNC) {
            researchTaskExecutor.execute(() -> executeTask(taskId));
            return;
        }
        executeTask(taskId);
    }

    /**
     * 按既定步骤顺序执行研究任务。
     */
    public void executeTask(UUID taskId) {
        ResearchTaskEntity task = loadTask(taskId);
        if (shouldStop(task)) {
            return;
        }

        if (task.getStartedAt() == null) {
            task.setStartedAt(OffsetDateTime.now());
            researchTaskRepository.save(task);
        }

        List<ResearchStepEntity> steps = researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId);
        for (ResearchStepEntity step : steps) {
            task = loadTask(taskId);
            if (shouldStop(task) || step.getStatus() == StepStatus.COMPLETED) {
                if (shouldStop(task)) {
                    return;
                }
                continue;
            }

            OffsetDateTime startedAt = OffsetDateTime.now();
            updateTaskForStep(task, step);

            StepExecutionEntity execution = createRunningExecution(taskId, step.getId(), startedAt, step.getInputPayload());
            step.setStatus(StepStatus.READY);
            researchStepRepository.save(step);
            taskEventService.recordSystemEvent(taskId, step.getId(), EventType.STEP_STARTED, "开始执行步骤：" + step.getTitle());

            try {
                ObjectNode outputPayload;
                // 报告步骤不走工具注册表，而是直接根据前序执行结果组装最终报告。
                if (step.getStepType() == StepType.REPORT) {
                    ResearchReportEntity report = researchReportAssemblyService.buildReport(taskId);
                    outputPayload = objectMapper.createObjectNode();
                    outputPayload.put("stepType", step.getStepType().name());
                    outputPayload.put("reportId", report.getId().toString());
                    outputPayload.put("summary", report.getSummary());
                } else {
                    // 其余步骤统一走工具注册表，保持编排层与具体工具实现解耦。
                    ResearchToolResult toolResult = researchToolRegistry.execute(
                            step.getStepType(),
                            new ResearchToolContext(task, step, sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(taskId))
                    );
                    persistSourceDocuments(taskId, step.getId(), toolResult.sourceDocuments());
                    if (step.getStepType() == StepType.CITATION_EXTRACT) {
                        markFetchedSourcesAsCitationReady(taskId);
                    }

                    outputPayload = objectMapper.createObjectNode();
                    outputPayload.put("stepType", step.getStepType().name());
                    outputPayload.set("result", toolResult.outputPayload());
                }

                completeExecution(task, step, execution, outputPayload, startedAt);
            } catch (Exception exception) {
                failExecution(task, step, execution, exception, startedAt);
                return;
            }
        }

        markTaskCompleted(taskId);
    }

    /**
     * 将任务状态切换到当前步骤对应的阶段。
     */
    private void updateTaskForStep(ResearchTaskEntity task, ResearchStepEntity step) {
        task.setCurrentStepId(step.getId());
        task.setStatus(TaskStatus.RUNNING);
        task.setCurrentStage(step.getStepType() == StepType.REPORT ? TaskStage.REPORTING : TaskStage.EXECUTING);
        researchTaskRepository.save(task);
        taskProgressStreamService.publish(task.getId(), "step-running", Map.of(
                "taskId", task.getId(),
                "stepId", step.getId(),
                "stepTitle", step.getTitle(),
                "stepType", step.getStepType().name()
        ));
    }

    /**
     * 创建运行中的步骤执行记录。
     */
    private StepExecutionEntity createRunningExecution(
            UUID taskId,
            UUID stepId,
            OffsetDateTime startedAt,
            com.fasterxml.jackson.databind.JsonNode inputPayload) {
        StepExecutionEntity execution = new StepExecutionEntity();
        execution.setTaskId(taskId);
        execution.setStepId(stepId);
        execution.setAttemptNo(1);
        execution.setExecutorType("SYSTEM");
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setInputPayload(inputPayload);
        execution.setStartedAt(startedAt);
        return stepExecutionRepository.save(execution);
    }

    /**
     * 处理步骤执行成功后的持久化与状态推进。
     */
    private void completeExecution(
            ResearchTaskEntity task,
            ResearchStepEntity step,
            StepExecutionEntity execution,
            ObjectNode outputPayload,
            OffsetDateTime startedAt) {
        OffsetDateTime finishedAt = OffsetDateTime.now();
        execution.setToolName(step.getStepType().name());
        execution.setStatus(ExecutionStatus.SUCCESS);
        execution.setOutputPayload(outputPayload);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        stepExecutionRepository.save(execution);

        step.setStatus(StepStatus.COMPLETED);
        researchStepRepository.save(step);
        promoteNextStep(task.getId(), step.getStepNo());

        taskEventService.recordSystemEvent(task.getId(), step.getId(), EventType.STEP_FINISHED, "步骤执行完成：" + step.getTitle());
        taskProgressStreamService.publish(task.getId(), "step-completed", Map.of(
                "taskId", task.getId(),
                "stepId", step.getId(),
                "stepTitle", step.getTitle(),
                "stepType", step.getStepType().name()
        ));
    }

    /**
     * 处理步骤执行失败时的任务收敛逻辑。
     */
    private void failExecution(
            ResearchTaskEntity task,
            ResearchStepEntity step,
            StepExecutionEntity execution,
            Exception exception,
            OffsetDateTime startedAt) {
        String errorMessage = exception.getMessage() != null ? exception.getMessage() : "步骤执行失败";
        OffsetDateTime finishedAt = OffsetDateTime.now();
        execution.setToolName(step.getStepType().name());
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        stepExecutionRepository.save(execution);

        step.setStatus(StepStatus.FAILED);
        researchStepRepository.save(step);

        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(OffsetDateTime.now());
        researchTaskRepository.save(task);

        taskEventService.recordSystemEvent(task.getId(), step.getId(), EventType.STEP_FAILED, "步骤执行失败：" + step.getTitle());
        taskProgressStreamService.publish(task.getId(), "task-status", Map.of(
                "taskId", task.getId(),
                "status", TaskStatus.FAILED.name(),
                "message", errorMessage
        ));
    }

    /**
     * 将工具返回的资料来源持久化到数据库。
     */
    private void persistSourceDocuments(UUID taskId, UUID stepId, List<ToolSourceDocument> sourceDocuments) {
        if (sourceDocuments == null || sourceDocuments.isEmpty()) {
            return;
        }

        List<SourceDocumentEntity> entities = sourceDocuments.stream().map(source -> {
            SourceDocumentEntity entity = new SourceDocumentEntity();
            entity.setTaskId(taskId);
            entity.setStepId(stepId);
            entity.setSourceType(source.sourceType());
            entity.setUrl(source.url());
            entity.setDomain(source.domain());
            entity.setTitle(source.title());
            entity.setSnippet(source.snippet());
            entity.setRawContent(source.rawContent());
            entity.setContentHash(source.contentHash());
            entity.setLanguage(source.language());
            entity.setFetchStatus(source.fetchStatus());
            entity.setRelevanceScore(source.relevanceScore());
            entity.setCitationReady(source.citationReady());
            entity.setMetadata(source.metadata());
            return entity;
        }).toList();

        sourceDocumentRepository.saveAll(entities);
    }

    /**
     * 将抓取后的来源标记为可用于引用。
     */
    private void markFetchedSourcesAsCitationReady(UUID taskId) {
        List<SourceDocumentEntity> fetchedSources = sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .filter(source -> source.getSourceType() == SourceType.FETCHED_PAGE)
                .toList();

        fetchedSources.forEach(source -> source.setCitationReady(true));
        sourceDocumentRepository.saveAll(fetchedSources);
    }

    /**
     * 将下一步置为就绪状态，便于后续执行与展示。
     */
    private void promoteNextStep(UUID taskId, int currentStepNo) {
        researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .filter(step -> step.getStepNo() == currentStepNo + 1)
                .findFirst()
                .ifPresent(step -> {
                    if (step.getStatus() == StepStatus.PENDING) {
                        step.setStatus(StepStatus.READY);
                        researchStepRepository.save(step);
                    }
                });
    }

    /**
     * 在所有步骤完成后收敛任务状态。
     */
    private void markTaskCompleted(UUID taskId) {
        ResearchTaskEntity task = loadTask(taskId);
        boolean allCompleted = researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .allMatch(step -> step.getStatus() == StepStatus.COMPLETED);
        if (!allCompleted) {
            return;
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCurrentStepId(null);
        task.setCompletedAt(OffsetDateTime.now());
        task.setCurrentStage(TaskStage.REPORTING);
        researchTaskRepository.save(task);

        taskEventService.recordSystemEvent(taskId, null, EventType.REPORT_GENERATED, "研究任务执行完成并已生成报告。");
        taskProgressStreamService.publish(taskId, "task-status", Map.of(
                "taskId", taskId,
                "status", TaskStatus.COMPLETED.name(),
                "message", "研究任务已完成"
        ));
    }

    /**
     * 判断当前任务是否应当停止继续执行。
     */
    private boolean shouldStop(ResearchTaskEntity task) {
        return task.getStatus() == TaskStatus.PAUSED
                || task.getStatus() == TaskStatus.CANCELLED
                || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.COMPLETED
                || task.getStatus() == TaskStatus.WAITING_FOR_CONFIRMATION;
    }

    /**
     * 统一加载任务实体。
     */
    private ResearchTaskEntity loadTask(UUID taskId) {
        return researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}
