package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchReportEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供研究报告的持久化访问能力。
 */
public interface ResearchReportRepository extends JpaRepository<ResearchReportEntity, UUID> {

    Optional<ResearchReportEntity> findByTaskId(UUID taskId);
}
