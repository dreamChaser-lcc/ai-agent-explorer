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
 * 研究任务编排器（核心执行引擎）。
 *
 * 职责：
 *   1. 按步骤顺序（SEARCH → FETCH → SUMMARIZE → CITATION_EXTRACT → REPORT）驱动执行
 *   2. 支持同步（SYNC）和异步（ASYNC）两种执行模式
 *   3. 每步执行前后：记录步骤状态、创建执行记录、推送 SSE 进度
 *   4. 处理执行失败时的任务收敛（标记 FAILED + 推送错误事件）
 *
 * 这是整个项目的"大脑"——连接 Controller → 计划 → 工具 → 报告。
 *
 * 同步模式（SYNC）：调用的线程会阻塞直到所有步骤执行完。
 *   适用场景：前端在创建任务后需要立即看到结果。
 *
 * 异步模式（ASYNC）：用线程池提交执行，主线程立即返回。
 *   适用场景：任务耗时长，前端通过 SSE 实时获取进度。
 */
@Service // 注册为 Spring Bean
public class ResearchTaskOrchestrator {

    // ===================== 依赖注入 =====================
    private final ResearchTaskRepository researchTaskRepository;         // 任务表
    private final ResearchStepRepository researchStepRepository;         // 步骤表
    private final StepExecutionRepository stepExecutionRepository;       // 步骤执行记录表
    private final SourceDocumentRepository sourceDocumentRepository;     // 资料来源表
    private final ResearchToolRegistry researchToolRegistry;             // 工具注册表（根据步骤类型分发工具）
    private final ResearchReportAssemblyService researchReportAssemblyService; // 报告组装服务
    private final TaskEventService taskEventService;                     // 事件记录服务
    private final TaskProgressStreamService taskProgressStreamService;   // SSE 进度推送
    private final ObjectMapper objectMapper;                             // JSON 序列化
    private final Executor researchTaskExecutor;                         // 异步执行线程池

    // ===================== 构造器注入 =====================
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
            @Qualifier("researchTaskExecutor") Executor researchTaskExecutor) { // @Qualifier 按名称注入指定的线程池 Bean
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
     *
     * ASYNC 模式：提交到线程池异步执行，主线程立即返回（不阻塞 Controller）
     * SYNC 模式：同步执行为止，适用为测试和简单场景
     *
     * @param taskId 任务 UUID
     */
    public void startExecution(UUID taskId) {
        ResearchTaskEntity task = loadTask(taskId); // 加载任务，校验是否存在

        if (task.getExecutionMode() == ExecutionMode.ASYNC) {
            // 异步模式：用 Runnable lambda 包装 executeTask，提交到线程池
            // Controller 收到返回后，前端通过 /api/tasks/{id}/stream 获取实时进度
            researchTaskExecutor.execute(() -> executeTask(taskId));
            return;
        }

        // 同步模式：直接在当前线程执行，所有步骤跑完才返回
        executeTask(taskId);
    }

    /**
     * 核心执行方法：按既定步骤顺序执行研究任务。
     *
     * 执行流程：
     *   1. 检查是否应该停止（暂停、取消、已完成等）
     *   2. 记录任务开始时间
     *   3. 遍历所有步骤（SEARCH → FETCH → SUMMARIZE → CITATION_EXTRACT → REPORT）
     *   4. 对每个步骤：
     *      a. 更新任务状态 → RUNNING
     *      b. 创建 StepExecutionEntity 执行记录
     *      c. 推送 SSE "步骤开始"
     *      d. 调用工具（通过 ResearchToolRegistry 分发）
     *      e. 持久化工具返回的资料来源
     *      f. 标记步骤完成，激活下一步
     *      g. 推送 SSE "步骤完成"
     *      （如果失败，标记失败并停止）
     *   5. 所有步骤完成后，标记任务为 COMPLETED
     *
     * @param taskId 任务 UUID
     */
    public void executeTask(UUID taskId) {
        // 1. 加载任务并检查
        ResearchTaskEntity task = loadTask(taskId);
        if (shouldStop(task)) { // 任务可能已被用户暂停或取消
            return;
        }

        // 2. 首次执行时记录开始时间
        if (task.getStartedAt() == null) {
            task.setStartedAt(OffsetDateTime.now());
            researchTaskRepository.save(task);
        }

        // 3. 按步骤序号升序获取所有步骤
        List<ResearchStepEntity> steps = researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId);

        // 4. 遍历执行每个步骤
        for (ResearchStepEntity step : steps) {
            // 每次循环前重新加载任务（可能被其他线程修改了状态）
            task = loadTask(taskId);
            if (shouldStop(task) || step.getStatus() == StepStatus.COMPLETED) {
                if (shouldStop(task)) {
                    return; // 任务被暂停/取消，停止执行
                }
                continue; // 步骤已完成，跳到下一个
            }

            // 记录步骤开始时间（用于计算耗时）
            OffsetDateTime startedAt = OffsetDateTime.now();
            // 更新任务状态为"执行中"
            updateTaskForStep(task, step);

            // 创建步骤执行为记录（状态：RUNNING）
            StepExecutionEntity execution = createRunningExecution(
                    taskId, step.getId(), startedAt, step.getInputPayload());
            step.setStatus(StepStatus.READY);
            researchStepRepository.save(step);

            // 记录"步骤开始"事件 + 推送 SSE
            taskEventService.recordSystemEvent(taskId, step.getId(),
                    EventType.STEP_STARTED, "开始执行步骤：" + step.getTitle());

            try {
                ObjectNode outputPayload; // 步骤输出的 JSON

                // ========== 分支：报告步骤 vs 普通步骤 ==========
                if (step.getStepType() == StepType.REPORT) {
                    // 报告步骤不走工具注册表，
                    // 而是通过 ResearchReportAssemblyService 汇总前序步骤的结果来组装最终报告。
                    ResearchReportEntity report = researchReportAssemblyService.buildReport(taskId);
                    outputPayload = objectMapper.createObjectNode();
                    outputPayload.put("stepType", step.getStepType().name());
                    outputPayload.put("reportId", report.getId().toString());
                    outputPayload.put("summary", report.getSummary());
                } else {
                    // 普通步骤：走工具注册表，根据 stepType 找到对应工具并执行
                    // 传入 ResearchToolContext（含任务信息 + 前序步骤积累的资料来源）
                    ResearchToolResult toolResult = researchToolRegistry.execute(
                            step.getStepType(),
                            new ResearchToolContext(
                                    task,
                                    step,
                                    sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                            )
                    );

                    // 把工具返回的资料来源持久化到数据库
                    persistSourceDocuments(taskId, step.getId(), toolResult.sourceDocuments());

                    // 引文提取步骤完成后，标记所有已抓取的来源为可引用
                    if (step.getStepType() == StepType.CITATION_EXTRACT) {
                        markFetchedSourcesAsCitationReady(taskId);
                    }

                    // 打包输出
                    outputPayload = objectMapper.createObjectNode();
                    outputPayload.put("stepType", step.getStepType().name());
                    outputPayload.set("result", toolResult.outputPayload());
                }

                // 步骤执行成功：更新执行记录、步骤状态、激活下一步、推送 SSE
                completeExecution(task, step, execution, outputPayload, startedAt);
            } catch (Exception exception) {
                // 步骤执行失败：标记失败、设置错误信息、推送 SSE 错误事件，然后停止
                failExecution(task, step, execution, exception, startedAt);
                return;
            }
        }

        // 5. 所有步骤执行完毕，标记任务完成
        markTaskCompleted(taskId);
    }

    // ==================== 以下为私有辅助方法 ====================

    /**
     * 将任务状态切换到当前步骤对应的阶段。
     * 推 送 SSE 事件告诉前端"哪个步骤正在运行"。
     */
    private void updateTaskForStep(ResearchTaskEntity task, ResearchStepEntity step) {
        task.setCurrentStepId(step.getId()); // 记录当前执行的步骤 ID
        task.setStatus(TaskStatus.RUNNING);   // 任务状态 → 运行中

        // 根据步骤类型判阶段：REPORT 步骤 → REPORTING 阶段，其余 → EXECUTING 阶段
        task.setCurrentStage(step.getStepType() == StepType.REPORT
                ? TaskStage.REPORTING
                : TaskStage.EXECUTING);

        researchTaskRepository.save(task);

        // 向所有 SSE 订阅连接广播"步骤开始运行"事件
        taskProgressStreamService.publish(task.getId(), "step-running", Map.of(
                "taskId", task.getId(),
                "stepId", step.getId(),
                "stepTitle", step.getTitle(),
                "stepType", step.getStepType().name()
        ));
    }

    /**
     * 创建运行中的步骤执行记录（StepExecutionEntity）。
     *
     * 每条执行记录记录了一次步骤运行的完整信息：什么时候开始、什么输入、什么结果、耗时多少。
     * 前端通过 /api/tasks/{taskId}/executions 获取这些记录来渲染时间线。
     */
    private StepExecutionEntity createRunningExecution(
            UUID taskId,
            UUID stepId,
            OffsetDateTime startedAt,
            com.fasterxml.jackson.databind.JsonNode inputPayload) {
        StepExecutionEntity execution = new StepExecutionEntity();
        execution.setTaskId(taskId);
        execution.setStepId(stepId);
        execution.setAttemptNo(1);             // 重试次数，首次执行 = 1（为后续重试预留）
        execution.setExecutorType("SYSTEM");   // 执行器类型（目前没有人工执行）
        execution.setStatus(ExecutionStatus.RUNNING); // 状态：运行中
        execution.setInputPayload(inputPayload);       // 保存原始输入
        execution.setStartedAt(startedAt);
        return stepExecutionRepository.save(execution);
    }

    /**
     * 处理步骤执行成功后的持久化与状态推进。
     *
     * 做的事情：
     *   1. 更新执行记录（状态→SUCCESS、记录耗时、保存输出）
     *   2. 更新步骤状态→COMPLETED
     *   3. 激活下一步（PENDING→READY）
     *   4. 记录事件 + SSE 广播
     */
    private void completeExecution(
            ResearchTaskEntity task, ResearchStepEntity step,
            StepExecutionEntity execution, ObjectNode outputPayload,
            OffsetDateTime startedAt) {

        OffsetDateTime finishedAt = OffsetDateTime.now();

        // 更新执行记录
        execution.setToolName(step.getStepType().name()); // 工具名 = 步骤类型（目前一一对应）
        execution.setStatus(ExecutionStatus.SUCCESS);
        execution.setOutputPayload(outputPayload);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(Duration.between(startedAt, finishedAt).toMillis()); // 计算执行耗时
        stepExecutionRepository.save(execution);

        // 更新步骤状态
        step.setStatus(StepStatus.COMPLETED);
        researchStepRepository.save(step);

        // 激活下一步（第 N 步完成后 → 第 N+1 步从 PENDING → READY）
        promoteNextStep(task.getId(), step.getStepNo());

        // 记录事件 + 推送 SSE
        taskEventService.recordSystemEvent(task.getId(), step.getId(),
                EventType.STEP_FINISHED, "步骤执行完成：" + step.getTitle());
        taskProgressStreamService.publish(task.getId(), "step-completed", Map.of(
                "taskId", task.getId(),
                "stepId", step.getId(),
                "stepTitle", step.getTitle(),
                "stepType", step.getStepType().name()
        ));
    }

    /**
     * 处理步骤执行失败时的任务收敛逻辑。
     *
     * 步骤失败 → 任务也标记为 FAILED → 不再执行后续步骤。
     */
    private void failExecution(
            ResearchTaskEntity task, ResearchStepEntity step,
            StepExecutionEntity execution, Exception exception,
            OffsetDateTime startedAt) {

        // 提取错误消息（异常对象不为空则取其 message，否则给默认提示）
        String errorMessage = exception.getMessage() != null
                ? exception.getMessage()
                : "步骤执行失败";

        OffsetDateTime finishedAt = OffsetDateTime.now();

        // 更新执行记录为失败
        execution.setToolName(step.getStepType().name());
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        stepExecutionRepository.save(execution);

        // 标记步骤失败
        step.setStatus(StepStatus.FAILED);
        researchStepRepository.save(step);

        // 标记任务失败
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(OffsetDateTime.now());
        researchTaskRepository.save(task);

        // 记录事件 + 推送 SSE 错误通知
        taskEventService.recordSystemEvent(task.getId(), step.getId(),
                EventType.STEP_FAILED, "步骤执行失败：" + step.getTitle());
        taskProgressStreamService.publish(task.getId(), "task-status", Map.of(
                "taskId", task.getId(),
                "status", TaskStatus.FAILED.name(),
                "message", errorMessage
        ));
    }

    /**
     * 将工具返回的资料来源（ToolSourceDocument）转换为数据库实体并批量保存。
     *
     * ToolSourceDocument 是工具层的 record，轻量、不可变，
     * SourceDocumentEntity 是 JPA 实体，有完整的数据库映射。
     * 这里做的是"record → entity"的转换。
     */
    private void persistSourceDocuments(UUID taskId, UUID stepId,
                                         List<ToolSourceDocument> sourceDocuments) {
        if (sourceDocuments == null || sourceDocuments.isEmpty()) {
            return; // 没有来源数据，跳过
        }

        // Stream 流式处理：每个 ToolSourceDocument 都转为一个 SourceDocumentEntity
        List<SourceDocumentEntity> entities = sourceDocuments.stream().map(source -> {
            SourceDocumentEntity entity = new SourceDocumentEntity();
            entity.setTaskId(taskId);
            entity.setStepId(stepId);
            entity.setSourceType(source.sourceType());       // 来源类型（搜索/抓取）
            entity.setUrl(source.url());                     // URL
            entity.setDomain(source.domain());               // 域名（用于引用展示）
            entity.setTitle(source.title());                 // 标题
            entity.setSnippet(source.snippet());             // 摘要片段
            entity.setRawContent(source.rawContent());       // 正文内容
            entity.setContentHash(source.contentHash());     // 内容哈希（用于去重）
            entity.setLanguage(source.language());           // 语言
            entity.setFetchStatus(source.fetchStatus());     // 抓取状态
            entity.setRelevanceScore(source.relevanceScore()); // 相关性得分
            entity.setCitationReady(source.citationReady());   // 是否可用于引用
            entity.setMetadata(source.metadata());           // 其他元数据 JSON
            return entity;
        }).toList();

        sourceDocumentRepository.saveAll(entities); // 批量保存
    }

    /**
     * 将抓取（FETCH）后的来源标记为可用于引用。
     * 在引文提取步骤（CITATION_EXTRACT）完成后调用，
     * 确保后续生成报告时能引用这些来源。
     */
    private void markFetchedSourcesAsCitationReady(UUID taskId) {
        // 筛选出来源类型为 FETCHED_PAGE 的记录
        List<SourceDocumentEntity> fetchedSources =
                sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                        .filter(source -> source.getSourceType() == SourceType.FETCHED_PAGE)
                        .toList();

        // 逐个标记 citationReady = true
        fetchedSources.forEach(source -> source.setCitationReady(true));
        sourceDocumentRepository.saveAll(fetchedSources);
    }

    /**
     * 将下一步（currentStepNo + 1）从 PENDING 置为 READY。
     *
     * 步骤状态流转：
     *   PENDING → READY → RUNNING → COMPLETED
     *                    ↑
     *             promoteNextStep 负责这个转换
     */
    private void promoteNextStep(UUID taskId, int currentStepNo) {
        researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .filter(step -> step.getStepNo() == currentStepNo + 1) // 找到下一步
                .findFirst()
                .ifPresent(step -> {
                    if (step.getStatus() == StepStatus.PENDING) {
                        step.setStatus(StepStatus.READY); // 激活
                        researchStepRepository.save(step);
                    }
                });
    }

    /**
     * 在所有步骤完成后收敛任务状态。
     * 双重校验：先确认所有步骤都是 COMPLETED 才标记完成。
     */
    private void markTaskCompleted(UUID taskId) {
        ResearchTaskEntity task = loadTask(taskId);

        // 校验：所有步骤是否都已完成
        boolean allCompleted = researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId).stream()
                .allMatch(step -> step.getStatus() == StepStatus.COMPLETED);
        if (!allCompleted) {
            return; // 有步骤未完成，不标记（防御性检查）
        }

        task.setStatus(TaskStatus.COMPLETED);       // 任务状态 → 已完成
        task.setCurrentStepId(null);                // 清除当前步骤
        task.setCompletedAt(OffsetDateTime.now());  // 记录完成时间
        task.setCurrentStage(TaskStage.REPORTING);  // 最终阶段：报告
        researchTaskRepository.save(task);

        // 记录事件 + 推送 SSE "任务完成"
        taskEventService.recordSystemEvent(taskId, null,
                EventType.REPORT_GENERATED, "研究任务执行完成并已生成报告。");
        taskProgressStreamService.publish(taskId, "task-status", Map.of(
                "taskId", taskId,
                "status", TaskStatus.COMPLETED.name(),
                "message", "研究任务已完成"
        ));
    }

    /**
     * 判断当前任务是否应当停执继续执行。
     *
     * 停执条件：已暂停 / 已取消 / 已失败 / 已完成 / 等待用户确认
     */
    private boolean shouldStop(ResearchTaskEntity task) {
        return task.getStatus() == TaskStatus.PAUSED
                || task.getStatus() == TaskStatus.CANCELLED
                || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.COMPLETED
                || task.getStatus() == TaskStatus.WAITING_FOR_CONFIRMATION;
    }

    /**
     * 统一的任务加载方法——找不到就抛 TaskNotFoundException。
     * 全局异常处理器会捕获并返回 404 JSON。
     */
    private ResearchTaskEntity loadTask(UUID taskId) {
        return researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}
