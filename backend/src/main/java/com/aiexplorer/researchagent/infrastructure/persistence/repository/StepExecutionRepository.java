package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.StepExecutionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供步骤执行记录的持久化访问能力。
 */
public interface StepExecutionRepository extends JpaRepository<StepExecutionEntity, UUID> {

    List<StepExecutionEntity> findByTaskIdOrderByStartedAtDesc(UUID taskId);
}
