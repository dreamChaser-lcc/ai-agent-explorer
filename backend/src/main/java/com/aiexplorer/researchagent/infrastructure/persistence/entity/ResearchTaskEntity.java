package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
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

/**
 * 持久化研究工作流的顶层任务记录。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "research_task")
public class ResearchTaskEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_no", nullable = false, unique = true, length = 64)
    private String taskNo;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 32)
    private ExecutionMode executionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 64)
    private TaskStage currentStage;

    @Column(name = "current_step_id")
    private UUID currentStepId;

    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    @Column(nullable = false)
    private int priority;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
