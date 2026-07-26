package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.StepStatus;
import com.aiexplorer.researchagent.shared.enums.StepType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 持久化研究任务中的计划步骤。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "research_step")
public class ResearchStepEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 64)
    private StepType stepType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", columnDefinition = "jsonb")
    private JsonNode inputPayload;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    private String expectedOutput;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private StepStatus status;

    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    @Column(name = "depends_on_step_id")
    private UUID dependsOnStepId;
}
