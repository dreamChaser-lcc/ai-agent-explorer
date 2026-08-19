package com.aiexplorer.researchagent.infrastructure.persistence.store;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import java.util.List;
import java.util.UUID;

/**
 * 任务事件日志的持久化访问抽象接口。
 *
 * <p>同一份数据访问能力存在两套实现（JPA / MyBatis），
 * 通过配置项 {@code app.persistence.mode} 决定装配哪一套。
 * Service 层只依赖本接口，不感知底层实现，切换无需改动业务代码。</p>
 */
public interface TaskEventLogStore {

    /**
     * 保存一条事件日志（新增或更新由各实现自行处理）。
     */
    void save(TaskEventLogEntity eventLogEntity);

    /**
     * 按任务 ID 查询事件日志，时间倒序。
     */
    List<TaskEventLogEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
