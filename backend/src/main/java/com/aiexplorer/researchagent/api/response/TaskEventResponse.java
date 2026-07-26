package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.OperatorType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 表示任务事件时间线中的单条记录。
 */
public record TaskEventResponse(
        UUID id,
        UUID taskId,
        UUID stepId,
        EventType eventType,
        String eventMessage,
        OperatorType operatorType,
        String operatorId,
        OffsetDateTime createdAt) {
}
