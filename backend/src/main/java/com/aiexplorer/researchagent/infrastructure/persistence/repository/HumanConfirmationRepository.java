package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.HumanConfirmationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供人工确认记录的持久化访问能力。
 */
public interface HumanConfirmationRepository extends JpaRepository<HumanConfirmationEntity, UUID> {

    List<HumanConfirmationEntity> findByTaskIdOrderByRequestedAtDesc(UUID taskId);
}
