package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.TaskEventLogRepository;
import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.OperatorType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 负责统一写入任务事件日志，并向 SSE 订阅方广播事件。
 */
@Service
public class TaskEventService {

    private final TaskEventLogRepository taskEventLogRepository;
    private final TaskProgressStreamService taskProgressStreamService;

    public TaskEventService(
            TaskEventLogRepository taskEventLogRepository,
            TaskProgressStreamService taskProgressStreamService) {
        this.taskEventLogRepository = taskEventLogRepository;
        this.taskProgressStreamService = taskProgressStreamService;
    }

    /**
     * 记录系统自动触发的任务事件。
     */
    public void recordSystemEvent(UUID taskId, UUID stepId, EventType eventType, String message) {
        saveEvent(taskId, stepId, eventType, message, OperatorType.SYSTEM, "system");
    }

    /**
     * 记录用户触发的任务事件。
     */
    public void recordUserEvent(UUID taskId, UUID stepId, EventType eventType, String message) {
        saveEvent(taskId, stepId, eventType, message, OperatorType.USER, "user");
    }

    /**
     * 保存事件并推送给当前任务的实时订阅方。
     */
    private void saveEvent(
            UUID taskId,
            UUID stepId,
            EventType eventType,
            String message,
            OperatorType operatorType,
            String operatorId) {
        TaskEventLogEntity event = new TaskEventLogEntity();
        event.setTaskId(taskId);
        event.setStepId(stepId);
        event.setEventType(eventType);
        event.setEventMessage(message);
        event.setOperatorType(operatorType);
        event.setOperatorId(operatorId);
        event.setCreatedAt(OffsetDateTime.now());
        taskEventLogRepository.save(event);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("stepId", stepId);
        payload.put("eventType", eventType.name());
        payload.put("message", message);
        payload.put("operatorType", operatorType.name());
        payload.put("createdAt", event.getCreatedAt().toString());
        taskProgressStreamService.publish(taskId, "task-event", payload);
    }
}
