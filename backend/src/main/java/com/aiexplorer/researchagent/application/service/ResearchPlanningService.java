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
 * 负责生成研究计划、步骤以及首个人工确认节点。
 */
@Service
public class ResearchPlanningService {

    private final ResearchTaskRepository researchTaskRepository;
    private final ResearchPlanRepository researchPlanRepository;
    private final ResearchStepRepository researchStepRepository;
    private final HumanConfirmationRepository humanConfirmationRepository;
    private final TaskEventService taskEventService;
    private final TaskProgressStreamService taskProgressStreamService;
    private final ObjectMapper objectMapper;

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
     * 为新任务生成首版研究计划、步骤清单和人工确认记录。
     */
    @Transactional
    public ResearchPlanEntity generateInitialPlan(UUID taskId) {
        ResearchTaskEntity task = researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        ResearchPlanEntity plan = new ResearchPlanEntity();
        plan.setTaskId(taskId);
        plan.setVersion(1);
        plan.setPlanSummary("系统已根据研究目标生成初版研究计划，等待用户确认后继续执行。");
        plan.setPlanObjective(task.getGoal());
        plan.setStatus(PlanStatus.GENERATED);
        plan.setConfirmationStatus(ConfirmationStatus.PENDING);
        plan.setPlannerModel("template-planner");
        plan.setPlannerPromptSnapshot("基于当前 MVP 阶段使用模板化规划逻辑生成研究计划。");
        plan.setRawPlanPayload(objectMapper.valueToTree(Map.of(
                "taskId", taskId,
                "goal", task.getGoal(),
                "steps", buildStepDefinitions(task.getGoal())
        )));

        ResearchPlanEntity savedPlan = researchPlanRepository.save(plan);

        // 当前 MVP 采用模板化步骤定义，先把完整步骤链持久化下来。
        List<ResearchStepEntity> steps = createSteps(taskId, savedPlan.getId(), task.getGoal());
        researchStepRepository.saveAll(steps);

        HumanConfirmationEntity confirmation = new HumanConfirmationEntity();
        confirmation.setTaskId(taskId);
        confirmation.setConfirmationType(ConfirmationType.PLAN_APPROVAL);
        confirmation.setStatus(ConfirmationStatus.PENDING);
        confirmation.setRequestMessage("请确认研究计划后继续执行。");
        confirmation.setRequestedAt(OffsetDateTime.now());
        confirmation.setRequestedBy("system");
        humanConfirmationRepository.save(confirmation);

        task.setStatus(TaskStatus.WAITING_FOR_CONFIRMATION);
        task.setCurrentStage(TaskStage.PLANNING);
        task.setRequiresConfirmation(true);
        researchTaskRepository.save(task);

        taskEventService.recordSystemEvent(taskId, null, EventType.PLAN_GENERATED, "已生成研究计划并等待人工确认。");
        taskProgressStreamService.publish(taskId, "plan-generated", Map.of(
                "taskId", taskId,
                "status", TaskStatus.WAITING_FOR_CONFIRMATION.name(),
                "message", "研究计划已生成，等待人工确认"
        ));
        return savedPlan;
    }

    /**
     * 生成当前 MVP 所需的固定步骤集合，后续可以替换为真正的 LLM 规划。
     */
    private List<ResearchStepEntity> createSteps(UUID taskId, UUID planId, String goal) {
        return List.of(
                buildStep(taskId, planId, 1, StepType.SEARCH, "搜索候选资料", "围绕研究目标检索候选资料来源", goal),
                buildStep(taskId, planId, 2, StepType.FETCH, "抓取资料正文", "抓取候选来源的正文内容", goal),
                buildStep(taskId, planId, 3, StepType.SUMMARIZE, "总结研究发现", "对抓取内容进行总结归纳", goal),
                buildStep(taskId, planId, 4, StepType.CITATION_EXTRACT, "提取引用信息", "整理用于报告展示的引用来源", goal),
                buildStep(taskId, planId, 5, StepType.REPORT, "生成研究报告", "汇总研究结果并生成最终报告", goal)
        );
    }

    /**
     * 构建单个研究步骤实体。
     */
    private ResearchStepEntity buildStep(
            UUID taskId,
            UUID planId,
            int stepNo,
            StepType stepType,
            String title,
            String description,
            String goal) {
        ResearchStepEntity step = new ResearchStepEntity();
        step.setTaskId(taskId);
        step.setPlanId(planId);
        step.setStepNo(stepNo);
        step.setStepType(stepType);
        step.setTitle(title);
        step.setDescription(description);
        step.setInputPayload(objectMapper.valueToTree(Map.of(
                "goal", goal,
                "stepType", stepType.name()
        )));
        step.setExpectedOutput("完成 " + title + " 对应的结构化结果。");
        step.setStatus(stepNo == 1 ? StepStatus.READY : StepStatus.PENDING);
        step.setRequiresConfirmation(false);
        return step;
    }

    /**
     * 构建序列化后的步骤定义，用于存储原始计划内容。
     */
    private List<Map<String, Object>> buildStepDefinitions(String goal) {
        return List.of(
                Map.of("stepNo", 1, "type", StepType.SEARCH.name(), "title", "搜索候选资料", "goal", goal),
                Map.of("stepNo", 2, "type", StepType.FETCH.name(), "title", "抓取资料正文", "goal", goal),
                Map.of("stepNo", 3, "type", StepType.SUMMARIZE.name(), "title", "总结研究发现", "goal", goal),
                Map.of("stepNo", 4, "type", StepType.CITATION_EXTRACT.name(), "title", "提取引用信息", "goal", goal),
                Map.of("stepNo", 5, "type", StepType.REPORT.name(), "title", "生成研究报告", "goal", goal)
        );
    }

}
