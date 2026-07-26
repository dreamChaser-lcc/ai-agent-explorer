package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchStepEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import com.aiexplorer.researchagent.shared.enums.ConfirmationStatus;
import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.PlanStatus;
import com.aiexplorer.researchagent.shared.enums.ReportStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
import com.aiexplorer.researchagent.shared.enums.StepStatus;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * 校验核心持久化实体与仓库查询在测试数据库中的行为。
 */
@DataJpaTest
class RepositoryPersistenceTest {

    @Autowired
    private ResearchTaskRepository researchTaskRepository;

    @Autowired
    private ResearchPlanRepository researchPlanRepository;

    @Autowired
    private ResearchStepRepository researchStepRepository;

    @Autowired
    private SourceDocumentRepository sourceDocumentRepository;

    @Autowired
    private ResearchReportRepository researchReportRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPersistTaskAndFillAuditTimestamps() {
        ResearchTaskEntity task = createTask("TASK-TEST-001");

        ResearchTaskEntity savedTask = researchTaskRepository.save(task);

        assertThat(savedTask.getId()).isNotNull();
        assertThat(savedTask.getCreatedAt()).isNotNull();
        assertThat(savedTask.getUpdatedAt()).isNotNull();
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void shouldReturnPlansOrderedByVersionDescending() {
        ResearchTaskEntity task = researchTaskRepository.save(createTask("TASK-TEST-002"));

        ResearchPlanEntity versionOne = new ResearchPlanEntity();
        versionOne.setTaskId(task.getId());
        versionOne.setVersion(1);
        versionOne.setPlanSummary("第一版计划");
        versionOne.setPlanObjective(task.getGoal());
        versionOne.setStatus(PlanStatus.GENERATED);
        versionOne.setConfirmationStatus(ConfirmationStatus.PENDING);
        versionOne.setPlannerModel("test-planner");
        versionOne.setRawPlanPayload(objectMapper.valueToTree(List.of("search", "fetch")));

        ResearchPlanEntity versionTwo = new ResearchPlanEntity();
        versionTwo.setTaskId(task.getId());
        versionTwo.setVersion(2);
        versionTwo.setPlanSummary("第二版计划");
        versionTwo.setPlanObjective(task.getGoal());
        versionTwo.setStatus(PlanStatus.CONFIRMED);
        versionTwo.setConfirmationStatus(ConfirmationStatus.APPROVED);
        versionTwo.setPlannerModel("test-planner");
        versionTwo.setRawPlanPayload(objectMapper.valueToTree(List.of("search", "fetch", "report")));

        researchPlanRepository.save(versionOne);
        researchPlanRepository.save(versionTwo);

        List<ResearchPlanEntity> plans = researchPlanRepository.findByTaskIdOrderByVersionDesc(task.getId());

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).getVersion()).isEqualTo(2);
        assertThat(plans.get(1).getVersion()).isEqualTo(1);
    }

    @Test
    void shouldReturnStepsOrderedByStepNumberAscending() {
        ResearchTaskEntity task = researchTaskRepository.save(createTask("TASK-TEST-003"));
        ResearchPlanEntity plan = researchPlanRepository.save(createPlan(task.getId(), 1));

        researchStepRepository.save(createStep(task.getId(), plan.getId(), 2, "抓取资料正文"));
        researchStepRepository.save(createStep(task.getId(), plan.getId(), 1, "搜索候选资料"));

        List<ResearchStepEntity> steps = researchStepRepository.findByTaskIdOrderByStepNoAsc(task.getId());

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getStepNo()).isEqualTo(1);
        assertThat(steps.get(0).getTitle()).isEqualTo("搜索候选资料");
        assertThat(steps.get(1).getStepNo()).isEqualTo(2);
    }

    @Test
    void shouldReturnSourcesOrderedByCreatedAtDescending() {
        ResearchTaskEntity task = researchTaskRepository.save(createTask("TASK-TEST-004"));
        OffsetDateTime baseTime = OffsetDateTime.now().minusMinutes(1);

        SourceDocumentEntity firstSource = createSource(task.getId(), "https://example.com/a", "来源 A");
        SourceDocumentEntity secondSource = createSource(task.getId(), "https://example.com/b", "来源 B");

        SourceDocumentEntity savedFirstSource = sourceDocumentRepository.saveAndFlush(firstSource);
        savedFirstSource.setCreatedAt(baseTime);
        sourceDocumentRepository.saveAndFlush(savedFirstSource);

        SourceDocumentEntity savedSecondSource = sourceDocumentRepository.saveAndFlush(secondSource);
        savedSecondSource.setCreatedAt(baseTime.plusMinutes(1));
        sourceDocumentRepository.saveAndFlush(savedSecondSource);

        List<SourceDocumentEntity> sources = sourceDocumentRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());

        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).getTitle()).isEqualTo("来源 B");
        assertThat(sources.get(1).getTitle()).isEqualTo("来源 A");
    }

    @Test
    void shouldFindReportByTaskId() {
        ResearchTaskEntity task = researchTaskRepository.save(createTask("TASK-TEST-005"));

        ResearchReportEntity report = new ResearchReportEntity();
        report.setTaskId(task.getId());
        report.setVersion(1);
        report.setSummary("这是研究报告摘要");
        report.setKeyFindings(objectMapper.valueToTree(List.of("发现 1", "发现 2")));
        report.setFinalRecommendation("建议继续完善真实 provider 接入。");
        report.setReportMarkdown("# 报告");
        report.setReportJson(objectMapper.valueToTree(List.of(task.getId().toString())));
        report.setStatus(ReportStatus.FINAL);

        researchReportRepository.save(report);

        ResearchReportEntity savedReport = researchReportRepository.findByTaskId(task.getId()).orElseThrow();

        assertThat(savedReport.getSummary()).isEqualTo("这是研究报告摘要");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.FINAL);
    }

    /**
     * 构造测试使用的任务实体。
     */
    private ResearchTaskEntity createTask(String taskNo) {
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setTaskNo(taskNo);
        task.setTitle("测试研究任务");
        task.setGoal("验证持久化层是否能够正确保存和查询研究数据。");
        task.setExecutionMode(ExecutionMode.ASYNC);
        task.setStatus(TaskStatus.QUEUED);
        task.setCurrentStage(TaskStage.PLANNING);
        task.setRequiresConfirmation(false);
        task.setPriority(0);
        task.setCreatedBy("test-user");
        return task;
    }

    /**
     * 构造测试使用的研究计划实体。
     */
    private ResearchPlanEntity createPlan(UUID taskId, int version) {
        ResearchPlanEntity plan = new ResearchPlanEntity();
        plan.setTaskId(taskId);
        plan.setVersion(version);
        plan.setPlanSummary("测试研究计划");
        plan.setPlanObjective("验证计划排序");
        plan.setStatus(PlanStatus.GENERATED);
        plan.setConfirmationStatus(ConfirmationStatus.PENDING);
        plan.setPlannerModel("test-planner");
        plan.setRawPlanPayload(objectMapper.valueToTree(List.of("step-1", "step-2")));
        return plan;
    }

    /**
     * 构造测试使用的步骤实体。
     */
    private ResearchStepEntity createStep(UUID taskId, UUID planId, int stepNo, String title) {
        ResearchStepEntity step = new ResearchStepEntity();
        step.setTaskId(taskId);
        step.setPlanId(planId);
        step.setStepNo(stepNo);
        step.setStepType(stepNo == 1 ? StepType.SEARCH : StepType.FETCH);
        step.setTitle(title);
        step.setDescription("测试步骤");
        step.setInputPayload(objectMapper.valueToTree(List.of("input")));
        step.setExpectedOutput("测试输出");
        step.setStatus(StepStatus.PENDING);
        step.setRequiresConfirmation(false);
        return step;
    }

    /**
     * 构造测试使用的来源资料实体。
     */
    private SourceDocumentEntity createSource(UUID taskId, String url, String title) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setTaskId(taskId);
        source.setSourceType(SourceType.FETCHED_PAGE);
        source.setUrl(url);
        source.setDomain("example.com");
        source.setTitle(title);
        source.setSnippet("测试摘要");
        source.setRawContent("测试正文");
        source.setContentHash(title);
        source.setLanguage("zh-CN");
        source.setFetchStatus(FetchStatus.SUCCESS);
        source.setRelevanceScore(0.9);
        source.setCitationReady(true);
        source.setMetadata(objectMapper.valueToTree(List.of("meta")));
        return source;
    }
}
