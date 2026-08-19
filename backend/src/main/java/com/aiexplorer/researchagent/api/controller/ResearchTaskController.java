package com.aiexplorer.researchagent.api.controller; // api 层的 controller 子包，存放所有 HTTP 接口控制器

// ===================== 请求 DTO 导入 =====================
import com.aiexplorer.researchagent.api.request.CreateResearchTaskRequest; // 创建任务的请求体
import com.aiexplorer.researchagent.api.request.PlanConfirmationRequest;   // 确认/拒绝计划的请求体

// ===================== 响应 DTO 导入 =====================
import com.aiexplorer.researchagent.api.response.StepExecutionResponse; // 步骤执行记录的响应
import com.aiexplorer.researchagent.api.response.TaskEventResponse;      // 任务事件的响应
import com.aiexplorer.researchagent.api.response.TaskDetailResponse;     // 任务详情的响应
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;    // 任务摘要的响应（列表用）

// ===================== 服务层导入 =====================
import com.aiexplorer.researchagent.application.service.TaskActivityQueryService;   // 查活动（步骤时间线、事件日志）
import com.aiexplorer.researchagent.application.service.TaskProgressStreamService;  // SSE 实时推送
import com.aiexplorer.researchagent.application.service.ResearchTaskCommandService; // 任务写操作（创建）
import com.aiexplorer.researchagent.application.service.ResearchTaskControlService; // 任务控制（暂停/恢复/取消/确认计划）
import com.aiexplorer.researchagent.application.service.ResearchTaskQueryService;   // 任务读操作（列表、详情）

// ===================== 框架注解导入 =====================
import jakarta.validation.Valid; // 触发请求体参数的 JSR-303 校验（@NotBlank, @NotNull 等）
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;                     // HTTP 状态码枚举
import org.springframework.web.bind.annotation.GetMapping;      // GET 请求映射
import org.springframework.web.bind.annotation.PathVariable;    // 从 URL 路径中提取变量（如 /api/tasks/{taskId}）
import org.springframework.web.bind.annotation.PostMapping;     // POST 请求映射
import org.springframework.web.bind.annotation.RequestBody;     // 从请求体中提取 JSON
import org.springframework.web.bind.annotation.RequestMapping;  // 类级别路径前缀
import org.springframework.web.bind.annotation.ResponseStatus;  // 自定义 HTTP 返回码
import org.springframework.web.bind.annotation.RestController;  // 声明这是 REST 控制器（返回 JSON）
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter; // SSE（Server-Sent Events）发射器，用于推送实时事件

/**
 * 研究任务 REST 控制器。
 *
 * 职责：接收 HTTP 请求 → 校验参数 → 委托给应用层 Service → 返回 JSON/SseEmitter
 *
 * 路由表（完整 URL = 类前缀 + 方法路径）：
 *   POST   /api/tasks                  → 创建任务
 *   GET    /api/tasks                  → 获取任务列表
 *   GET    /api/tasks/{taskId}         → 获取任务详情
 *   GET    /api/tasks/{taskId}/executions → 获取步骤执行时间线
 *   GET    /api/tasks/{taskId}/events    → 获取任务事件日志
 *   GET    /api/tasks/{taskId}/stream    → 订阅 SSE 进度推送
 *   POST   /api/tasks/{taskId}/confirm   → 确认/拒绝研究计划
 *   POST   /api/tasks/{taskId}/pause     → 暂停任务
 *   POST   /api/tasks/{taskId}/resume    → 恢复任务
 *   POST   /api/tasks/{taskId}/cancel    → 取消任务
 */
@RestController             // 等价于 @Controller + @ResponseBody，每个方法返回 JSON
@RequestMapping("/api/tasks") // 类级别路径前缀：该控制器下所有接口都从 /api/tasks 开始
public class ResearchTaskController {

    // ===================== 依赖注入（构造器注入方式，Spring 推荐） =====================
    // 这些字段用 final 修饰，确保注入后不被修改
    private final ResearchTaskCommandService researchTaskCommandService; // 任务写操作：创建
    private final ResearchTaskQueryService researchTaskQueryService;     // 任务读操作：列表、详情
    private final ResearchTaskControlService researchTaskControlService; // 任务控制：暂停、恢复、取消、确认计划
    private final TaskActivityQueryService taskActivityQueryService;     // 活动查询：步骤时间线、事件日志
    private final TaskProgressStreamService taskProgressStreamService;   // SSE 实时进度推送

    /**
     * 构造器注入：Spring 会自动从容器中找出所有参数类型的 Bean 并传入。
     * 这是推荐的注入方式，不需要 @Autowired 注解。
     */
    public ResearchTaskController(
            ResearchTaskCommandService researchTaskCommandService,
            ResearchTaskQueryService researchTaskQueryService,
            ResearchTaskControlService researchTaskControlService,
            TaskActivityQueryService taskActivityQueryService,
            TaskProgressStreamService taskProgressStreamService) {
        this.researchTaskCommandService = researchTaskCommandService;
        this.researchTaskQueryService = researchTaskQueryService;
        this.researchTaskControlService = researchTaskControlService;
        this.taskActivityQueryService = taskActivityQueryService;
        this.taskProgressStreamService = taskProgressStreamService;
    }

    /**
     * 创建研究任务并立即触发初版计划生成。
     *
     * POST /api/tasks
     * 请求体示例：
     *   { "title": "Java Agent 架构研究", "goal": "调研主流...", "executionMode": "SYNC" }
     *
     * @param request 前端提交的研究任务信息，@Valid 会触发字段校验
     * @return 创建后的任务摘要（含 TaskNo、状态等）
     */
    @PostMapping               // 处理 POST /api/tasks
    @ResponseStatus(HttpStatus.CREATED) // 成功时返回 201 Created
    public TaskSummaryResponse createTask(@Valid @RequestBody CreateResearchTaskRequest request) {
        // 委托给命令服务，写死 createdBy 为 "demo-user"（MVP 阶段没有用户系统）
        return researchTaskCommandService.createTask(request, "demo-user");
    }

    /**
     * 返回所有已创建的任务摘要列表（按时间倒序）。
     *
     * GET /api/tasks
     */
    @GetMapping                // 处理 GET /api/tasks
    public List<TaskSummaryResponse> listTasks() {
        return researchTaskQueryService.listTasks();
    }

    /**
     * 返回单个任务的详情视图（含计划摘要、步骤列表）。
     *
     * GET /api/tasks/{taskId}
     */
    @GetMapping("/{taskId}")   // {taskId} 是路径变量，自动绑定到 @PathVariable
    public TaskDetailResponse getTaskDetail(@PathVariable UUID taskId) {
        return researchTaskQueryService.getTaskDetail(taskId);
    }

    /**
     * 返回任务的步骤执行时间线（所有步骤的执行记录、耗时、状态）。
     *
     * GET /api/tasks/{taskId}/executions
     */
    @GetMapping("/{taskId}/executions")
    public List<StepExecutionResponse> listExecutions(@PathVariable UUID taskId) {
        return taskActivityQueryService.listStepExecutions(taskId);
    }

    /**
     * 返回任务事件日志（创建、计划生成、确认、暂停等全生命周期事件）。
     *
     * GET /api/tasks/{taskId}/events
     */
    @GetMapping("/{taskId}/events")
    public List<TaskEventResponse> listTaskEvents(@PathVariable UUID taskId) {
        return taskActivityQueryService.listTaskEvents(taskId);
    }

    /**
     * 建立 SSE 长连接，前端通过 EventSource 订阅实时进度。
     *
     * GET /api/tasks/{taskId}/stream
     * 前端用法：new EventSource("http://localhost:8080/api/tasks/{id}/stream")
     *
     * 该连接会持续推送事件，直到前端 close 或任务完成。
     */
    @GetMapping("/{taskId}/stream")
    public SseEmitter streamTaskProgress(@PathVariable UUID taskId) {
        return taskProgressStreamService.subscribe(taskId);
    }

    /**
     * 提交研究计划确认结果。
     *
     * POST /api/tasks/{taskId}/confirm
     * 请求体示例：
     *   { "approved": true, "responseMessage": "" }   → 批准计划
     *   { "approved": false, "responseMessage": "重新生成" } → 拒绝计划
     *
     * 批准后任务进入执行阶段，拒绝后任务状态回退。
     */
    @PostMapping("/{taskId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 返回 204 No Content（无响应体）
    public void confirmPlan(
            @PathVariable UUID taskId,
            @Valid @RequestBody PlanConfirmationRequest request) {
        researchTaskControlService.confirmPlan(taskId, request.approved(), request.responseMessage());
    }

    /**
     * 暂停正在执行的任务。
     *
     * POST /api/tasks/{taskId}/pause
     */
    @PostMapping("/{taskId}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pauseTask(@PathVariable UUID taskId) {
        researchTaskControlService.pauseTask(taskId);
    }

    /**
     * 恢复已暂停的任务。
     *
     * POST /api/tasks/{taskId}/resume
     */
    @PostMapping("/{taskId}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resumeTask(@PathVariable UUID taskId) {
        researchTaskControlService.resumeTask(taskId);
    }

    /**
     * 取消任务执行。
     *
     * POST /api/tasks/{taskId}/cancel
     */
    @PostMapping("/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelTask(@PathVariable UUID taskId) {
        researchTaskControlService.cancelTask(taskId);
    }
}
