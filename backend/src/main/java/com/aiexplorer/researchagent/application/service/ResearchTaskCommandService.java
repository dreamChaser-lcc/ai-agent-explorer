package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.request.CreateResearchTaskRequest;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责处理研究任务创建相关的写操作。
 */
@Service
public class ResearchTaskCommandService {

    private final ResearchTaskRepository researchTaskRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final ResearchPlanningService researchPlanningService;

    public ResearchTaskCommandService(
            ResearchTaskRepository researchTaskRepository,
            TaskResponseMapper taskResponseMapper,
            ResearchPlanningService researchPlanningService) {
        this.researchTaskRepository = researchTaskRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.researchPlanningService = researchPlanningService;
    }

    /**
     * 创建研究任务主记录，并在同一事务中触发初版计划生成。
     */
    @Transactional
    public TaskSummaryResponse createTask(CreateResearchTaskRequest request, String createdBy) {
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setTaskNo(generateTaskNo());
        task.setTitle(request.title().trim());
        task.setGoal(request.goal().trim());
        task.setExecutionMode(request.executionMode());
        task.setStatus(TaskStatus.QUEUED);
        task.setCurrentStage(TaskStage.PLANNING);
        task.setRequiresConfirmation(false);
        task.setPriority(0);
        task.setCreatedBy(createdBy);

        ResearchTaskEntity savedTask = researchTaskRepository.save(task);
        // 任务创建后立即进入规划阶段，确保前端拿到的任务已经具备计划上下文。
        researchPlanningService.generateInitialPlan(savedTask.getId());

        ResearchTaskEntity plannedTask = researchTaskRepository.findById(savedTask.getId())
                .orElse(savedTask);
        return taskResponseMapper.toTaskSummary(plannedTask);
    }

    /**
     * 生成便于展示和排查问题的任务编号。
     */
    private String generateTaskNo() {
        return "TASK-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}
