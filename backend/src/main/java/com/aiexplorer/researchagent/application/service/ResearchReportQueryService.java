package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.response.ResearchReportResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchReportRepository;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责读取研究报告相关数据。
 */
@Service
public class ResearchReportQueryService {

    private final ResearchReportRepository researchReportRepository;
    private final TaskResponseMapper taskResponseMapper;

    public ResearchReportQueryService(
            ResearchReportRepository researchReportRepository,
            TaskResponseMapper taskResponseMapper) {
        this.researchReportRepository = researchReportRepository;
        this.taskResponseMapper = taskResponseMapper;
    }

    /**
     * 根据任务编号读取最终研究报告。
     */
    @Transactional(readOnly = true)
    public ResearchReportResponse getReportByTaskId(UUID taskId) {
        ResearchReportEntity report = researchReportRepository.findByTaskId(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        return taskResponseMapper.toResearchReport(report);
    }
}
