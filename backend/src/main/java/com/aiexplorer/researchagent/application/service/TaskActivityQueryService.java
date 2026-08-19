package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.api.response.StepExecutionResponse;
import com.aiexplorer.researchagent.api.response.TaskEventResponse;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.StepExecutionRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.store.TaskEventLogStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责读取任务事件和步骤执行时间线。
 */
@Service
public class TaskActivityQueryService {

    private final StepExecutionRepository stepExecutionRepository;
    private final TaskEventLogStore taskEventLogStore;
    private final TaskResponseMapper taskResponseMapper;

    public TaskActivityQueryService(
            StepExecutionRepository stepExecutionRepository,
            TaskEventLogStore taskEventLogStore,
            TaskResponseMapper taskResponseMapper) {
        this.stepExecutionRepository = stepExecutionRepository;
        this.taskEventLogStore = taskEventLogStore;
        this.taskResponseMapper = taskResponseMapper;
    }

    /**
     * 返回任务对应的步骤执行时间线。
     */
    @Transactional(readOnly = true)
    public List<StepExecutionResponse> listStepExecutions(UUID taskId) {
        return stepExecutionRepository.findByTaskIdOrderByStartedAtDesc(taskId).stream()
                .map(taskResponseMapper::toStepExecution)
                .toList();
    }

    /**
     * 返回任务对应的事件日志列表。
     */
    @Transactional(readOnly = true)
    public List<TaskEventResponse> listTaskEvents(UUID taskId) {
        return taskEventLogStore.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(taskResponseMapper::toTaskEvent)
                .toList();
    }
}
