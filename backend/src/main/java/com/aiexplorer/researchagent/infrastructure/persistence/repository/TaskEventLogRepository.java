package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提供任务事件日志的持久化访问能力。
 */
public interface TaskEventLogRepository extends JpaRepository<TaskEventLogEntity, UUID> {

    List<TaskEventLogEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
