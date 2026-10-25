package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.api.response.ResearchReportResponse;
import com.aiexplorer.researchagent.application.service.ResearchReportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露研究报告查询接口。
 */
@RestController
@RequestMapping("/api/tasks/{taskId}/report")
@Tag(name = "研究报告", description = "查询任务执行完成后生成的最终研究报告")
public class ResearchReportController {

    private final ResearchReportQueryService researchReportQueryService;

    public ResearchReportController(ResearchReportQueryService researchReportQueryService) {
        this.researchReportQueryService = researchReportQueryService;
    }

    /**
     * 根据任务编号查询最终研究报告。
     */
    @Operation(summary = "查询任务报告", description = "任务未完成时返回 409；任务不存在返回 404")
    @GetMapping
    public ResearchReportResponse getReport(@PathVariable UUID taskId) {
        return researchReportQueryService.getReportByTaskId(taskId);
    }
}
