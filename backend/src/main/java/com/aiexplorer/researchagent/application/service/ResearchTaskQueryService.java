package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.response.PageResponse;
import com.aiexplorer.researchagent.api.response.TaskDetailResponse;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchPlanRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.ResearchTaskMapper;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 负责研究任务列表与详情的查询操作。
 *
 * <p>场景③ 起：列表查询走 MyBatis 手写 SQL（ResearchTaskMapper）；
 * 详情查询仍走 JPA（findById）——两条读路径并存。</p>
 */
@Service
public class ResearchTaskQueryService {

    private final ResearchTaskRepository researchTaskRepository;
    private final ResearchPlanRepository researchPlanRepository;
    private final ResearchStepRepository researchStepRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final ResearchTaskMapper researchTaskMapper; // ★ 列表分页查询（MyBatis 手写 SQL）

    public ResearchTaskQueryService(
            ResearchTaskRepository researchTaskRepository,
            ResearchPlanRepository researchPlanRepository,
            ResearchStepRepository researchStepRepository,
            TaskResponseMapper taskResponseMapper,
            ResearchTaskMapper researchTaskMapper) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchPlanRepository = researchPlanRepository;
        this.researchStepRepository = researchStepRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.researchTaskMapper = researchTaskMapper;
    }

    /** 每页条数上限：防止 size=100000 之类的请求把数据库拖垮（接口防御——场景① 精神的延续）。 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 分页 + 筛选返回任务摘要列表（按创建时间倒序）。
     *
     * <p>实现路径：MyBatis 手写 SQL（见 mapper/ResearchTaskMapper.xml）——
     * 排序与分页全部下推到数据库，只把这 N 条数据带进内存。</p>
     *
     * @param page   页码（从 0 开始；负数按 0 处理）
     * @param size   每页条数（服务端夹取到 1 ~ 100）
     * @param status 状态筛选（null = 全部）
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> listTasks(int page, int size, TaskStatus status) {
        // ① 参数防御：夹取到安全范围
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        // ② 换算 SQL 的 OFFSET：offset = page × size
        //    注意转 long：大页码时 int 会溢出
        long offset = (long) safePage * safeSize;

        // ③ 查本页数据 + 查总数（两条 SQL 的条件必须一致）
        List<ResearchTaskEntity> rows = researchTaskMapper.findPage(status, safeSize, offset);
        long totalElements = researchTaskMapper.countPage(status);

        // ④ 实体 → 响应（沿用既有翻译官）
        List<TaskSummaryResponse> content = rows.stream()
                .map(taskResponseMapper::toTaskSummary)
                .toList();

        // ⑤ 总页数 = 向上取整：(总数 + 每页 - 1) / 每页
        int totalPages = (int) ((totalElements + safeSize - 1) / safeSize);

        return new PageResponse<>(content, safePage, safeSize, totalElements, totalPages);
    }

    /**
     * 查询单个任务详情，并附带最新计划摘要和步骤列表。
     */
    @Transactional(readOnly = true)
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
