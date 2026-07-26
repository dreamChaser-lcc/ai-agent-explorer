package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.ReportStatus;
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
 * 持久化最终生成的结构化研究报告。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "research_report")
public class ResearchReportEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false, unique = true)
    private UUID taskId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_findings", columnDefinition = "jsonb")
    private JsonNode keyFindings;

    @Column(name = "final_recommendation", columnDefinition = "TEXT")
    private String finalRecommendation;

    @Column(name = "report_markdown", columnDefinition = "TEXT")
    private String reportMarkdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", columnDefinition = "jsonb")
    private JsonNode reportJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ReportStatus status;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;
}
