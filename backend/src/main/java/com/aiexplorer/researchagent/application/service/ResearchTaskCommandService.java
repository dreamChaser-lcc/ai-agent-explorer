package com.aiexplorer.researchagent.application.service; // 应用层服务包

import com.aiexplorer.researchagent.api.request.CreateResearchTaskRequest; // 创建任务请求 DTO（来自 api 层）
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;      // 任务摘要响应 DTO（返回给 api 层）
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity; // 数据库实体
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository; // 数据库操作接口
import com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.ResearchTaskMapper;  // ★ MyBatis：幂等键查询（场景④）
import com.aiexplorer.researchagent.shared.annotation.AuditLog; // 审计注解（场景⑤：贴了它的方法自动留痕）
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
 *   1. 创建任务记录并持久化到数据库（★ 场景④ 起：支持幂等键——重复提交不重复创建）
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
    private final ResearchTaskMapper researchTaskMapper;         // ★ MyBatis：幂等键查询（场景④）

    public ResearchTaskCommandService(
            ResearchTaskRepository researchTaskRepository,
            TaskResponseMapper taskResponseMapper,
            ResearchPlanningService researchPlanningService,
            ResearchTaskMapper researchTaskMapper) {
        this.researchTaskRepository = researchTaskRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.researchPlanningService = researchPlanningService;
        this.researchTaskMapper = researchTaskMapper;
    }

    /**
     * 创建研究任务主记录，并在同一事务中触发初版计划生成。
     *
     * ★ 场景④ 幂等语义（check-then-act + 唯一约束兜底）：
     *   ① 快路径：带了幂等键且该键已处理过 → 直接返回【首次创建的任务】（重放，newlyCreated=false）；
     *   ② 创建路径：把幂等键写进任务记录，saveAndFlush 让唯一索引【当场】仲裁；
     *   ③ 并发窗口内的晚到者：撞唯一索引 → DataIntegrityViolationException
     *      → 值班室转 409"请重试"（重试即命中 ①，拿到首次结果）。
     *
     * @param request   前端传来的创建任务请求（title、goal、executionMode、可选 idempotencyKey）
     * @param createdBy 创建者（MVP 硬编码为 "demo-user"）
     * @return 创建结果（任务摘要 + 本次是否真新建——供 Controller 决定 201/200）
     */
    @AuditLog("CREATE_TASK") // 场景⑤：审计留痕——创建操作（target_id 为 null——创建时还没有任务 id）
    @Transactional // 确保以下所有数据库操作在同一个事务中，要么全部成功要么全部回滚
    public CreateTaskOutcome createTask(CreateResearchTaskRequest request, String createdBy) {
        // ★ 场景④ 快路径：幂等键命中 → 这是重复请求（双击 / 超时重试）→ 直接返回首次结果，不再创建
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            ResearchTaskEntity existing = researchTaskMapper.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                return new CreateTaskOutcome(taskResponseMapper.toTaskSummary(existing), false); // 重放
            }
        }

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
        task.setIdempotencyKey(idempotencyKey);         // ★ 记录幂等键（null = 不参与幂等）

        // 2. ★ saveAndFlush（而非 save）：立即执行 INSERT——
        //    让"唯一索引冲突"【当场抛出】，而不是拖到事务提交时（那时已出了本方法，无法优雅处理）。
        //    这是并发窗口的最终仲裁：两个同键请求同时到达 → 先到的成功、后到的在此撞墙
        //    → DataIntegrityViolationException → 值班室 409（重试即走上面的快路径重放）。
        ResearchTaskEntity savedTask = researchTaskRepository.saveAndFlush(task);

        // 3. 任务创建后立即进入规划阶段，触发计划生成
        //    任务创建后立即进入规划阶段，确保前端拿到的任务已经具备计划上下文。
        researchPlanningService.generateInitialPlan(savedTask.getId());

        // 4. 重新从数据库加载（因为 generateInitialPlan 可能修改了任务状态）
        ResearchTaskEntity plannedTask = researchTaskRepository.findById(savedTask.getId())
                .orElse(savedTask);
        // 5. 转化为前端需要的响应格式返回（newlyCreated=true：本次真的创建了）
        return new CreateTaskOutcome(taskResponseMapper.toTaskSummary(plannedTask), true);
    }

    /** 空白幂等键统一视为"没带"（null）——trim 后为空即无效。 */
    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim();
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
