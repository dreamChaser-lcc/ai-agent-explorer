package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.ConfirmationStatus;
import com.aiexplorer.researchagent.shared.enums.ConfirmationType;
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
 * 持久化计划或步骤审批使用的人工确认检查点。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "human_confirmation")
public class HumanConfirmationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "step_id")
    private UUID stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_type", nullable = false, length = 64)
    private ConfirmationType confirmationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ConfirmationStatus status;

    @Column(name = "request_message", nullable = false, columnDefinition = "TEXT")
    private String requestMessage;

    @Column(name = "response_message", columnDefinition = "TEXT")
    private String responseMessage;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(name = "responded_by", length = 128)
    private String respondedBy;
}
