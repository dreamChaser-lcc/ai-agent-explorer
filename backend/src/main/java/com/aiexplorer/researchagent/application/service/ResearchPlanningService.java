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

    public ResearchPlanningService(
            ResearchTaskRepository researchTaskRepository,
            ResearchPlanRepository researchPlanRepository,
            ResearchStepRepository researchStepRepository,
            HumanConfirmationRepository humanConfirmationRepository,
            TaskEventService taskEventService,
            TaskProgressStreamService taskProgressStreamService,
            ObjectMapper objectMapper) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchPlanRepository = researchPlanRepository;
        this.researchStepRepository = researchStepRepository;
        this.humanConfirmationRepository = humanConfirmationRepository;
        this.taskEventService = taskEventService;
        this.taskProgressStreamService = taskProgressStreamService;
        this.objectMapper = objectMapper;
    }

    /**
     * 为新任务生成首版研究计划、5 个步骤和人工确认记录。
     *
     * 执行流程（整个方法在一个数据库事务中）：
     *   1. 加载任务实体（找不到抛 TaskNotFoundException）
     *   2. 创建 ResearchPlanEntity（版本=1，状态=GENERATED）
     *   3. 生成 5 个 ResearchStepEntity（SEARCH → FETCH → SUMMARIZE → CITATION_EXTRACT → REPORT）
     *   4. 创建 HumanConfirmationEntity（等待用户确认计划）
     *   5. 更新任务状态 → WAITING_FOR_CONFIRMATION
     *   6. 记录事件 + 推送 SSE
     *
     * @param taskId 任务 UUID
     * @return 保存后的计划实体
     */
    @Transactional // 数据库事务：确保计划、步骤、确认记录要么全成功要么全回滚
    public ResearchPlanEntity generateInitialPlan(UUID taskId) {
        // 1. 加载任务，找不到抛异常（全局异常处理器会捕获并返回 404）
        ResearchTaskEntity task = researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        // 2. 创建计划实体
        ResearchPlanEntity plan = new ResearchPlanEntity();
        plan.setTaskId(taskId);                            // 关联任务
        plan.setVersion(1);                                // 首版计划
        plan.setPlanSummary("系统已根据研究目标生成初版研究计划，等待用户确认后继续执行。");
        plan.setPlanObjective(task.getGoal());              // 研究目标从任务中复制
        plan.setStatus(PlanStatus.GENERATED);               // 计划状态：已生成
        plan.setConfirmationStatus(ConfirmationStatus.PENDING); // 等待确认
        plan.setPlannerModel("template-planner");           // 当前使用模板规划器
        plan.setPlannerPromptSnapshot("基于当前 MVP 阶段使用模板化规划逻辑生成研究计划。");
        // 原始计划载荷：将步骤定义序列化为 JSON 存储
        plan.setRawPlanPayload(objectMapper.valueToTree(Map.of(
                "taskId", taskId,
                "goal", task.getGoal(),
                "steps", buildStepDefinitions(task.getGoal())
        )));

        ResearchPlanEntity savedPlan = researchPlanRepository.save(plan);

        // 3. 生成 5 个固定步骤，持久化到数据库
        List<ResearchStepEntity> steps = createSteps(taskId, savedPlan.getId(), task.getGoal());
        researchStepRepository.saveAll(steps); // 批量保存

        // 4. 创建人工确认记录（等待用户在页面上点击"确认"或"拒绝"）
        HumanConfirmationEntity confirmation = new HumanConfirmationEntity();
        confirmation.setTaskId(taskId);
        confirmation.setConfirmationType(ConfirmationType.PLAN_APPROVAL); // 类型：计划审批
        confirmation.setStatus(ConfirmationStatus.PENDING);               // 待处理
        confirmation.setRequestMessage("请确认研究计划后继续执行。");
        confirmation.setRequestedAt(OffsetDateTime.now());                // 请求时间
        confirmation.setRequestedBy("system");                            // 系统发起
        humanConfirmationRepository.save(confirmation);

        // 5. 更新任务状态
        task.setStatus(TaskStatus.WAITING_FOR_CONFIRMATION); // 等待用户确认
        task.setCurrentStage(TaskStage.PLANNING);             // 仍在规划阶段
        task.setRequiresConfirmation(true);                   // 标记需要确认
        researchTaskRepository.save(task);

        // 6. 记录事件（事件日志 + SSE 推送）
        taskEventService.recordSystemEvent(taskId, null, EventType.PLAN_GENERATED,
                "已生成研究计划并等待人工确认。");
        taskProgressStreamService.publish(taskId, "plan-generated", Map.of(
                "taskId", taskId,
                "status", TaskStatus.WAITING_FOR_CONFIRMATION.name(),
                "message", "研究计划已生成，等待人工确认"
        ));
        return savedPlan;
    }

    /**
     * 生成当前 MVP 所需的固定 5 步骤集合。
     *
     * 后续可替换为真正的 LLM 动态规划：先调用 LangChain4j 让 LLM 分析研究目标，
     * 然后生成定制化的步骤列表。
     */
    private List<ResearchStepEntity> createSteps(UUID taskId, UUID planId, String goal) {
        return List.of(
                buildStep(taskId, planId, 1, StepType.SEARCH, "搜索候选资料",
                        "围绕研究目标检索候选资料来源", goal),
                buildStep(taskId, planId, 2, StepType.FETCH, "抓取资料正文",
                        "抓取候选来源的正文内容", goal),
                buildStep(taskId, planId, 3, StepType.SUMMARIZE, "总结研究发现",
                        "对抓取内容进行总结归纳", goal),
                buildStep(taskId, planId, 4, StepType.CITATION_EXTRACT, "提取引用信息",
                        "整理用于报告展示的引用来源", goal),
                buildStep(taskId, planId, 5, StepType.REPORT, "生成研究报告",
                        "汇总研究结果并生成最终报告", goal)
        );
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
     */
    private List<Map<String, Object>> buildStepDefinitions(String goal) {
        return List.of(
                Map.of("stepNo", 1, "type", StepType.SEARCH.name(),
                        "title", "搜索候选资料", "goal", goal),
                Map.of("stepNo", 2, "type", StepType.FETCH.name(),
                        "title", "抓取资料正文", "goal", goal),
                Map.of("stepNo", 3, "type", StepType.SUMMARIZE.name(),
                        "title", "总结研究发现", "goal", goal),
                Map.of("stepNo", 4, "type", StepType.CITATION_EXTRACT.name(),
                        "title", "提取引用信息", "goal", goal),
                Map.of("stepNo", 5, "type", StepType.REPORT.name(),
                        "title", "生成研究报告", "goal", goal)
        );
    }

}
