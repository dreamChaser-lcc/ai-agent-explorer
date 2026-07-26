package com.aiexplorer.researchagent.infrastructure.persistence.entity;

import com.aiexplorer.researchagent.shared.enums.FetchStatus;
import com.aiexplorer.researchagent.shared.enums.SourceType;
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
 * 持久化研究流程中采集和处理后的来源材料。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "source_document")
public class SourceDocumentEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "step_id")
    private UUID stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private SourceType sourceType;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(length = 255)
    private String domain;

    @Column(length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(length = 32)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false, length = 64)
    private FetchStatus fetchStatus;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "citation_ready", nullable = false)
    private boolean citationReady;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;
}
