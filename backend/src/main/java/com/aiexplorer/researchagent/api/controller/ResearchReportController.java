package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.api.response.ResearchReportResponse;
import com.aiexplorer.researchagent.application.service.ResearchReportQueryService;
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
public class ResearchReportController {

    private final ResearchReportQueryService researchReportQueryService;

    public ResearchReportController(ResearchReportQueryService researchReportQueryService) {
        this.researchReportQueryService = researchReportQueryService;
    }

    /**
     * 根据任务编号查询最终研究报告。
     */
    @GetMapping
    public ResearchReportResponse getReport(@PathVariable UUID taskId) {
        return researchReportQueryService.getReportByTaskId(taskId);
    }
}
