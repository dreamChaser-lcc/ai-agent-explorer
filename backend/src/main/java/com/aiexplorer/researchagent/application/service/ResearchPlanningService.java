package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.HumanConfirmationEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchStepEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.HumanConfirmationRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchPlanRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.shared.enums.ConfirmationStatus;
import com.aiexplorer.researchagent.shared.enums.ConfirmationType;
import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.PlanStatus;
import com.aiexplorer.researchagent.shared.enums.StepStatus;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 研究计划服务。
 *
 * 职责：为新任务自动生成研究计划、步骤清单和人工确认记录。
 *
 * 当前 MVP 阶段使用"模板化规划"：直接用写死的 5 步模板生成计划，
 * 而不是通过 LLM 动态规划。后续可替换为真正的 LangChain4j 规划。
 *
 * 生成的 5 步模板：
 *   步骤 1: SEARCH           → 搜索候选资料
 *   步骤 2: FETCH            → 抓取资料正文
 *   步骤 3: SUMMARIZE        → 总结研究发现
 *   步骤 4: CITATION_EXTRACT → 提取引用信息
 *   步骤 5: REPORT           → 生成研究报告
 */
@Service // 注册为 Spring Bean，被 ResearchTaskCommandService 调用
public class ResearchPlanningService {

    // ===================== 依赖注入 =====================
    private final ResearchTaskRepository researchTaskRepository;           // 任务表操作
    private final ResearchPlanRepository researchPlanRepository;           // 计划表操作
    private final ResearchStepRepository researchStepRepository;           // 步骤表操作
    private final HumanConfirmationRepository humanConfirmationRepository; // 确认表操作
    private final TaskEventService taskEventService;                       // 事件记录 + SSE 广播
    private final TaskProgressStreamService taskProgressStreamService;     // SSE 进度推送
    private final ObjectMapper objectMapper;                               // JSON 序列化工具（Spring 自动注入）
    private final LlmPlanGenerator llmPlanGenerator;                       // 场景⑧：LLM 计划生成（含超时/重试/降级）

    public ResearchPlanningService(
            ResearchTaskRepository researchTaskRepository,
            ResearchPlanRepository researchPlanRepository,
            ResearchStepRepository researchStepRepository,
            HumanConfirmationRepository humanConfirmationRepository,
            TaskEventService taskEventService,
            TaskProgressStreamService taskProgressStreamService,
            ObjectMapper objectMapper,
            LlmPlanGenerator llmPlanGenerator) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchPlanRepository = researchPlanRepository;
        this.researchStepRepository = researchStepRepository;
        this.humanConfirmationRepository = humanConfirmationRepository;
        this.taskEventService = taskEventService;
        this.taskProgressStreamService = taskProgressStreamService;
        this.objectMapper = objectMapper;
        this.llmPlanGenerator = llmPlanGenerator;
    }

    /**
     * 为新任务生成首版研究计划、5 个步骤和人工确认记录。
     *
     * 执行流程（整个方法在一个数据库事务中）：
     *   1. 加载任务实体（找不到抛 TaskNotFoundException）
     *   2. 生成计划草稿：优先 LLM（超时/重试/降级由 LlmCallGuard + LlmPlanGenerator 治理），
     *      LLM 不可用时自动降级为内置 5 步模板（场景⑧）
     *   3. 创建 ResearchPlanEntity（版本=1，状态=GENERATED，plannerModel 记录来源）
     *   4. 按草稿生成 ResearchStepEntity（LLM 版本或模板版本）
     *   5. 创建 HumanConfirmationEntity（等待用户确认计划）
     *   6. 更新任务状态 → WAITING_FOR_CONFIRMATION
     *   7. 记录事件（降级时额外记 LLM_DEGRADED）+ 推送 SSE
     *
     * @param taskId 任务 UUID
     * @return 保存后的计划实体
     */
    @Transactional // 数据库事务：确保计划、步骤、确认记录要么全成功要么全回滚
    public ResearchPlanEntity generateInitialPlan(UUID taskId) {
        // 1. 加载任务，找不到抛异常（全局异常处理器会捕获并返回 404）
        ResearchTaskEntity task = researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        // 2. 生成计划草稿（场景⑧：LLM 优先，失败降级模板——绝不让用户"任务建不出来"）
        PlanDraft planDraft = llmPlanGenerator.generate(task.getGoal());

        // 3. 创建计划实体
        ResearchPlanEntity plan = new ResearchPlanEntity();
        plan.setTaskId(taskId);                            // 关联任务
        plan.setVersion(1);                                // 首版计划
        plan.setPlanSummary(planDraft.summary());           // 摘要来自 LLM 或降级模板
        plan.setPlanObjective(task.getGoal());              // 研究目标从任务中复制
        plan.setStatus(PlanStatus.GENERATED);               // 计划状态：已生成
        plan.setConfirmationStatus(ConfirmationStatus.PENDING); // 等待确认
        plan.setPlannerModel(planDraft.plannerModel());     // 计划来源：llm:模型名 / template-fallback
        plan.setPlannerPromptSnapshot(planDraft.promptSnapshot()); // 提示词快照（复盘用）
        // 原始计划载荷：将步骤定义序列化为 JSON 存储
        plan.setRawPlanPayload(objectMapper.valueToTree(Map.of(
                "taskId", taskId,
                "goal", task.getGoal(),
                "degraded", planDraft.degraded(),
                "steps", buildStepDefinitions(planDraft.steps())
        )));

        ResearchPlanEntity savedPlan = researchPlanRepository.save(plan);

        // 4. 按草稿生成步骤，持久化到数据库（LLM 版步骤数可能不是 5 个）
        List<ResearchStepEntity> steps = createSteps(taskId, savedPlan.getId(), task.getGoal(), planDraft.steps());
        researchStepRepository.saveAll(steps); // 批量保存

        // 5. 创建人工确认记录（等待用户在页面上点击"确认"或"拒绝"）
        HumanConfirmationEntity confirmation = new HumanConfirmationEntity();
        confirmation.setTaskId(taskId);
        confirmation.setConfirmationType(ConfirmationType.PLAN_APPROVAL); // 类型：计划审批
        confirmation.setStatus(ConfirmationStatus.PENDING);               // 待处理
        confirmation.setRequestMessage("请确认研究计划后继续执行。");
        confirmation.setRequestedAt(OffsetDateTime.now());                // 请求时间
        confirmation.setRequestedBy("system");                            // 系统发起
        humanConfirmationRepository.save(confirmation);

        // 6. 更新任务状态
        task.setStatus(TaskStatus.WAITING_FOR_CONFIRMATION); // 等待用户确认
        task.setCurrentStage(TaskStage.PLANNING);             // 仍在规划阶段
        task.setRequiresConfirmation(true);                   // 标记需要确认
        researchTaskRepository.save(task);

        // 7. 记录事件（事件日志 + SSE 推送）
        // 降级留痕：LLM 不可用时"发生了什么"必须可追溯（沿用场景⑤的审计思维）
        if (planDraft.degraded()) {
            taskEventService.recordSystemEvent(taskId, null, EventType.LLM_DEGRADED,
                    "LLM 计划生成失败，已降级为模板计划。原因：" + planDraft.degradedReason());
        }
        taskEventService.recordSystemEvent(taskId, null, EventType.PLAN_GENERATED,
                planDraft.degraded()
                        ? "已生成研究计划（LLM 不可用，模板降级）并等待人工确认。"
                        : "已根据 LLM 生成研究计划并等待人工确认。");
        taskProgressStreamService.publish(taskId, "plan-generated", Map.of(
                "taskId", taskId,
                "status", TaskStatus.WAITING_FOR_CONFIRMATION.name(),
                "plannerModel", planDraft.plannerModel(),
                "degraded", planDraft.degraded(),
                "message", planDraft.degraded()
                        ? "研究计划已生成（LLM 不可用，已降级为模板），等待人工确认"
                        : "研究计划已生成，等待人工确认"
        ));
        return savedPlan;
    }

    /**
     * 按计划草稿生成步骤实体集合。
     *
     * 历史沿革：原实现是写死的固定 5 步（模板规划）。
     * 场景⑧ 已接入 LLM 动态规划——步骤来自 LLM（通常 3-5 个）；
     * LLM 不可用时降级回模板，两种来源在此汇合，统一转成 ResearchStepEntity。
     */
    private List<ResearchStepEntity> createSteps(
            UUID taskId, UUID planId, String goal, List<PlanDraft.StepDraft> stepDrafts) {
        List<ResearchStepEntity> stepEntities = new ArrayList<>();
        for (PlanDraft.StepDraft stepDraft : stepDrafts) {
            stepEntities.add(buildStep(taskId, planId, stepDraft.stepNo(),
                    resolveStepType(stepDraft.stepType()),
                    stepDraft.title(), stepDraft.description(), goal));
        }
        return stepEntities;
    }

    /**
     * 步骤类型字符串 → StepType 枚举。
     *
     * 草稿层已做白名单过滤，这里再兜底一次：不认识的类型一律按 REPORT 处理，
     * 避免一个脏字段导致整份计划落库失败。
     */
    private StepType resolveStepType(String stepTypeText) {
        try {
            return StepType.valueOf(stepTypeText);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return StepType.REPORT;
        }
    }

    /**
     * 构建单个研究步骤实体。
     *
     * 第 1 步初始状态为 READY（可直接执行），其余步骤为 PENDING（等待前一步完成再激活）。
     */
    private ResearchStepEntity buildStep(
            UUID taskId, UUID planId, int stepNo,
            StepType stepType, String title, String description, String goal) {
        ResearchStepEntity step = new ResearchStepEntity();
        step.setTaskId(taskId);
        step.setPlanId(planId);
        step.setStepNo(stepNo);          // 步骤序号 1-5
        step.setStepType(stepType);       // 步骤类型（决定用哪个工具）
        step.setTitle(title);
        step.setDescription(description);
        // 输入载荷：将研究目标和步骤类型打包为 JSON，传给工具
        step.setInputPayload(objectMapper.valueToTree(Map.of(
                "goal", goal,
                "stepType", stepType.name()
        )));
        step.setExpectedOutput("完成 " + title + " 对应的结构化结果。");
        // 第 1 步直接 READY，后续步骤 PENDING（等前一步完成后由 promoteNextStep 激活）
        step.setStatus(stepNo == 1 ? StepStatus.READY : StepStatus.PENDING);
        step.setRequiresConfirmation(false);
        return step;
    }

    /**
     * 构建序列化后的步骤定义列表，存入 rawPlanPayload 字段。
     * 用于保存计划的"原始内容"，方便后续复盘和调试。
     *
     * 场景⑧：定义不再写死，而是来自计划草稿（LLM 或降级模板），
     * 这样 rawPlanPayload 记录的是"这次真实用到的步骤"，可直接对比 LLM 版与模板版差异。
     */
    private List<Map<String, Object>> buildStepDefinitions(List<PlanDraft.StepDraft> stepDrafts) {
        List<Map<String, Object>> stepDefinitions = new ArrayList<>();
        for (PlanDraft.StepDraft stepDraft : stepDrafts) {
            stepDefinitions.add(Map.of(
                    "stepNo", stepDraft.stepNo(),
                    "type", stepDraft.stepType(),
                    "title", stepDraft.title(),
                    "description", stepDraft.description()));
        }
        return stepDefinitions;
    }

}
