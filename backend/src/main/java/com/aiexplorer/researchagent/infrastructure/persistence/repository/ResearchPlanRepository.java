package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchPlanEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供研究计划的持久化访问能力。
 */
public interface ResearchPlanRepository extends JpaRepository<ResearchPlanEntity, UUID> {

    List<ResearchPlanEntity> findByTaskIdOrderByVersionDesc(UUID taskId);
}
