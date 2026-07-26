package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.OperatorType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 持久化任务执行过程中产生的可审计工作流事件。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "task_event_log")
public class TaskEventLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "step_id")
    private UUID stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;

    @Column(name = "event_message", nullable = false, columnDefinition = "TEXT")
    private String eventMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", columnDefinition = "jsonb")
    private JsonNode eventPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_type", nullable = false, length = 32)
    private OperatorType operatorType;

    @Column(name = "operator_id", length = 128)
    private String operatorId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
