package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.api.request.CreateResearchTaskRequest;
import com.aiexplorer.researchagent.api.request.PlanConfirmationRequest;
import com.aiexplorer.researchagent.api.response.StepExecutionResponse;
import com.aiexplorer.researchagent.api.response.TaskEventResponse;
import com.aiexplorer.researchagent.api.response.TaskDetailResponse;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.application.service.TaskActivityQueryService;
import com.aiexplorer.researchagent.application.service.TaskProgressStreamService;
import com.aiexplorer.researchagent.application.service.ResearchTaskCommandService;
import com.aiexplorer.researchagent.application.service.ResearchTaskControlService;
import com.aiexplorer.researchagent.application.service.ResearchTaskQueryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 暴露研究任务创建、查询和控制相关接口。
 */
@RestController
@RequestMapping("/api/tasks")
public class ResearchTaskController {

    private final ResearchTaskCommandService researchTaskCommandService;
    private final ResearchTaskQueryService researchTaskQueryService;
    private final ResearchTaskControlService researchTaskControlService;
    private final TaskActivityQueryService taskActivityQueryService;
    private final TaskProgressStreamService taskProgressStreamService;

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
     * 创建研究任务，并立即触发初版计划生成。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskSummaryResponse createTask(@Valid @RequestBody CreateResearchTaskRequest request) {
        return researchTaskCommandService.createTask(request, "demo-user");
    }

    /**
     * 返回任务摘要列表。
     */
    @GetMapping
    public List<TaskSummaryResponse> listTasks() {
        return researchTaskQueryService.listTasks();
    }

    /**
     * 返回单个任务的详情视图。
     */
    @GetMapping("/{taskId}")
    public TaskDetailResponse getTaskDetail(@PathVariable UUID taskId) {
        return researchTaskQueryService.getTaskDetail(taskId);
    }

    /**
     * 返回任务的步骤执行时间线。
     */
    @GetMapping("/{taskId}/executions")
    public List<StepExecutionResponse> listExecutions(@PathVariable UUID taskId) {
        return taskActivityQueryService.listStepExecutions(taskId);
    }

    /**
     * 返回任务事件日志。
     */
    @GetMapping("/{taskId}/events")
    public List<TaskEventResponse> listTaskEvents(@PathVariable UUID taskId) {
        return taskActivityQueryService.listTaskEvents(taskId);
    }

    /**
     * 建立指定任务的 SSE 进度订阅连接。
     */
    @GetMapping("/{taskId}/stream")
    public SseEmitter streamTaskProgress(@PathVariable UUID taskId) {
        return taskProgressStreamService.subscribe(taskId);
    }

    /**
     * 提交研究计划确认结果。
     */
    @PostMapping("/{taskId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPlan(
            @PathVariable UUID taskId,
            @Valid @RequestBody PlanConfirmationRequest request) {
        researchTaskControlService.confirmPlan(taskId, request.approved(), request.responseMessage());
    }

    /**
     * 暂停任务执行。
     */
    @PostMapping("/{taskId}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pauseTask(@PathVariable UUID taskId) {
        researchTaskControlService.pauseTask(taskId);
    }

    /**
     * 恢复任务执行。
     */
    @PostMapping("/{taskId}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resumeTask(@PathVariable UUID taskId) {
        researchTaskControlService.resumeTask(taskId);
    }

    /**
     * 取消任务执行。
     */
    @PostMapping("/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelTask(@PathVariable UUID taskId) {
        researchTaskControlService.cancelTask(taskId);
    }
}
