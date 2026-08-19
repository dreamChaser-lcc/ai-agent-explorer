package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.store.TaskEventLogStore;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * MyBatis 实现：基于 Mapper 接口 + XML 手写 SQL。
 *
 * <p>装配条件：{@code app.persistence.mode=mybatis}。
 * 对应教学对照点：主键不再由 @GeneratedValue 自动生成，需手动补齐；
 * 保存/查询全部走 XML 中手写的 SQL。</p>
 */
@Repository
@ConditionalOnProperty(name = "app.persistence.mode", havingValue = "mybatis")
public class MyBatisTaskEventLogStore implements TaskEventLogStore {

    private final TaskEventLogMapper taskEventLogMapper;

    public MyBatisTaskEventLogStore(TaskEventLogMapper taskEventLogMapper) {
        this.taskEventLogMapper = taskEventLogMapper;
    }

    @Override
    public void save(TaskEventLogEntity eventLogEntity) {
        // JPA 的 @GeneratedValue 在 MyBatis 中不生效，主键需手动生成后写入
        if (eventLogEntity.getId() == null) {
            eventLogEntity.setId(UUID.randomUUID());
        }
        taskEventLogMapper.insert(eventLogEntity);
    }

    @Override
    public List<TaskEventLogEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId) {
        return taskEventLogMapper.findByTaskIdOrderByCreatedAtDesc(taskId);
    }
}
