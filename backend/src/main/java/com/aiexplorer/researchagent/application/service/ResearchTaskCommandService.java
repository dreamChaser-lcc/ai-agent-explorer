package com.aiexplorer.researchagent.application.service; // 应用层服务包

import com.aiexplorer.researchagent.api.request.CreateResearchTaskRequest; // 创建任务请求 DTO（来自 api 层）
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;      // 任务摘要响应 DTO（返回给 api 层）
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity; // 数据库实体
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository; // 数据库操作接口
import com.aiexplorer.researchagent.shared.enums.TaskStage;  // 任务阶段枚举（PLANNING / EXECUTING / REPORTING）
import com.aiexplorer.researchagent.shared.enums.TaskStatus; // 任务状态枚举（QUEUED / RUNNING / COMPLETED ...）
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;             // @Service：标记这是业务服务 Bean
import org.springframework.transaction.annotation.Transactional; // @Transactional：开启数据库事务

/**
 * 研究任务命令服务（写操作）。
 *
 * 职责：
 *   1. 创建任务记录并持久化到数据库
 *   2. 生成任务编号（TASK-xxxxxxxx）
 *   3. 触发初版研究计划的自动生成（委托给 ResearchPlanningService）
 *
 * 该类是"创建任务"这个用例的写入口，Controller 调用它，它再调用 PlanningService 和 Repository。
 */
@Service // Spring 会自动扫描并注册为 Bean
public class ResearchTaskCommandService {

    // ===================== 依赖注入 =====================
    private final ResearchTaskRepository researchTaskRepository; // JPA 仓储，操作 research_task 表
    private final TaskResponseMapper taskResponseMapper;         // 实体 → 响应 DTO 转换器
    private final ResearchPlanningService researchPlanningService; // 计划生成服务

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
     *
     * 执行流程：
     *   1. new ResearchTaskEntity() → set 各项字段 → save 到数据库
     *   2. researchPlanningService.generateInitialPlan(taskId) → 生成 5 步研究计划
     *   3. 重新加载任务，转换为响应 DTO 返回
     *
     * @param request   前端传来的创建任务请求（title、goal、executionMode）
     * @param createdBy 创建者（MVP 硬编码为 "demo-user"）
     * @return 创建为的置任务摘要
     */
    @Transactional // 确保以下所有数据库操作在同一个事务中，要么全部成功要么全部回滚
    public TaskSummaryResponse createTask(CreateResearchTaskRequest request, String createdBy) {
        // 1. 创建实体对象，逐字段填充
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setTaskNo(generateTaskNo());             // 生成展示编号 TASK-xxxxxxxx
        task.setTitle(request.title().trim());        // 去首尾空格
        task.setGoal(request.goal().trim());          // 去首尾空格
        task.setExecutionMode(request.executionMode()); // SYNC 或 ASYNC
        task.setStatus(TaskStatus.QUEUED);             // 初始状态：排队中
        task.setCurrentStage(TaskStage.PLANNING);      // 当前阶段：规划中
        task.setRequiresConfirmation(false);            // 初始不需要确认
        task.setPriority(0);                            // 默认优先级 0
        task.setCreatedBy(createdBy);                   // 创建者

        // 2. 保存到数据库，save() 是 JpaRepository 提供的方法，自动生成 INSERT SQL
        ResearchTaskEntity savedTask = researchTaskRepository.save(task);

        // 3. 任务创建后立即进入规划阶段，触发计划生成
        //    任务创建后立即进入规划阶段，确保前端拿到的任务已经具备计划上下文。
        researchPlanningService.generateInitialPlan(savedTask.getId());

        // 4. 重新从数据库加载（因为 generateInitialPlan 可能修改了任务状态）
        ResearchTaskEntity plannedTask = researchTaskRepository.findById(savedTask.getId())
                .orElse(savedTask);
        // 5. 转化为前端需要的响应格式并返回
        return taskResponseMapper.toTaskSummary(plannedTask);
    }

    /**
     * 生成便于展示和人工排查的任务编号。
     *
     * 格式：TASK-{UUID 前 8 位大写}，例如 TASK-A1B2C3D4
     * 这样比直接显示完整 UUID（36 位）更友好。
     */
    private String generateTaskNo() {
        return "TASK-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)   // 取前 8 位
                .toUpperCase(Locale.ROOT); // 转大写
    }
}
