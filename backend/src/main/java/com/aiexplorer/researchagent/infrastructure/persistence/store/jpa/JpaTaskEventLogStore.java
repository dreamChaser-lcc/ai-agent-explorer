package com.aiexplorer.researchagent.infrastructure.persistence.store.jpa;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.TaskEventLogRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.store.TaskEventLogStore;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * JPA 实现：基于 Spring Data JPA 自动生成 SQL，无手写 SQL。
 *
 * <p>装配条件：{@code app.persistence.mode=jpa}（未配置时默认使用本实现）。
 * 对应教学对照点：save() 自动判断 INSERT/UPDATE，findBy... 方法名即查询。</p>
 */
@Repository
@ConditionalOnProperty(name = "app.persistence.mode", havingValue = "jpa", matchIfMissing = true)
public class JpaTaskEventLogStore implements TaskEventLogStore {

    private final TaskEventLogRepository taskEventLogRepository;

    public JpaTaskEventLogStore(TaskEventLogRepository taskEventLogRepository) {
        this.taskEventLogRepository = taskEventLogRepository;
    }

    @Override
    public void save(TaskEventLogEntity eventLogEntity) {
        taskEventLogRepository.save(eventLogEntity);
    }

    @Override
    public List<TaskEventLogEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId) {
        return taskEventLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }
}
