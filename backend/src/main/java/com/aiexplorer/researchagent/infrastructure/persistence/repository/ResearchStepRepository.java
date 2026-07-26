package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchStepEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供研究步骤的持久化访问能力。
 */
public interface ResearchStepRepository extends JpaRepository<ResearchStepEntity, UUID> {

    List<ResearchStepEntity> findByTaskIdOrderByStepNoAsc(UUID taskId);
}
