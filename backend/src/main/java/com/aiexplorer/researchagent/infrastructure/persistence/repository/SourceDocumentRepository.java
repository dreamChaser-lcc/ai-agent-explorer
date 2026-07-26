package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供采集到的来源文档的持久化访问能力。
 */
public interface SourceDocumentRepository extends JpaRepository<SourceDocumentEntity, UUID> {

    List<SourceDocumentEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
