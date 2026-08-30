package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.response.TaskDetailResponse;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchPlanRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责研究任务列表与详情的查询操作。
 */
@Service
public class ResearchTaskQueryService {

    private final ResearchTaskRepository researchTaskRepository;
    private final ResearchPlanRepository researchPlanRepository;
    private final ResearchStepRepository researchStepRepository;
    private final TaskResponseMapper taskResponseMapper;

    public ResearchTaskQueryService(
            ResearchTaskRepository researchTaskRepository,
            ResearchPlanRepository researchPlanRepository,
            ResearchStepRepository researchStepRepository,
            TaskResponseMapper taskResponseMapper) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchPlanRepository = researchPlanRepository;
        this.researchStepRepository = researchStepRepository;
        this.taskResponseMapper = taskResponseMapper;
    }

    /**
     * 按创建时间倒序返回任务摘要列表。
     */
    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listTasks() {
        return researchTaskRepository.findAll().stream()
                .sorted(Comparator.comparing(ResearchTaskEntity::getCreatedAt).reversed())
                .map(taskResponseMapper::toTaskSummary)
                .toList();
    }

    /**
     * 查询单个任务详情，并附带最新计划摘要和步骤列表。
     *
     * 缓存说明：任务详情是前端轮询进度的高频读取场景，通过 @Cacheable 将结果缓存到 Redis。
     *   - 首次调用：查询数据库并写入缓存（key = task:detail::<taskId>）
     *   - 后续调用：缓存命中直接返回，不再访问数据库
     *   - 缓存有效期由 RedisCacheConfiguration 统一控制（默认 30 秒）
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "task:detail", key = "#taskId")
    public TaskDetailResponse getTaskDetail(UUID taskId) {
        ResearchTaskEntity task = researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        ResearchPlanEntity latestPlan = researchPlanRepository.findByTaskIdOrderByVersionDesc(taskId).stream()
                .findFirst()
                .orElse(null);

        return taskResponseMapper.toTaskDetail(
                task,
                latestPlan,
                researchStepRepository.findByTaskIdOrderByStepNoAsc(taskId)
        );
    }
}
