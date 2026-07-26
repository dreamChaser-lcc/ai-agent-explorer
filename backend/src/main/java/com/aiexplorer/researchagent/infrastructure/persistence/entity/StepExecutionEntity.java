package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.ExecutionStatus;
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
 * 持久化研究步骤的一次具体执行尝试。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "step_execution")
public class StepExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "executor_type", nullable = false, length = 32)
    private String executorType;

    @Column(name = "tool_name", length = 128)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ExecutionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", columnDefinition = "jsonb")
    private JsonNode inputPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    private JsonNode outputPayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;
}
