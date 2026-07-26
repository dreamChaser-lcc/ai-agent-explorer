package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.ConfirmationStatus;
import com.aiexplorer.researchagent.shared.enums.PlanStatus;
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
 * 持久化任务生成出的研究计划。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "research_plan")
public class ResearchPlanEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private int version;

    @Column(name = "plan_summary", nullable = false, columnDefinition = "TEXT")
    private String planSummary;

    @Column(name = "plan_objective", nullable = false, columnDefinition = "TEXT")
    private String planObjective;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private PlanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", nullable = false, length = 64)
    private ConfirmationStatus confirmationStatus;

    @Column(name = "planner_model", length = 128)
    private String plannerModel;

    @Column(name = "planner_prompt_snapshot", columnDefinition = "TEXT")
    private String plannerPromptSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_plan_payload", columnDefinition = "jsonb")
    private JsonNode rawPlanPayload;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
