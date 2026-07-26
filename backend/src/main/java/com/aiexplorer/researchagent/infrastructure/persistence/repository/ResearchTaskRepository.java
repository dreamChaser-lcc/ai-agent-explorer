package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供研究任务的持久化访问能力。
 */
public interface ResearchTaskRepository extends JpaRepository<ResearchTaskEntity, UUID> {
}
