package com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis 版任务事件日志数据访问接口。
 *
 * <p>接口只声明方法签名，SQL 全部维护在同名 XML 中
 * （resources/mapper/TaskEventLogMapper.xml），实现"接口与 SQL 分离"。</p>
 */
@Mapper
public interface TaskEventLogMapper {

    int insert(TaskEventLogEntity eventLogEntity);

    TaskEventLogEntity findById(UUID id);

    List<TaskEventLogEntity> findByTaskIdOrderByCreatedAtDesc(@Param("taskId") UUID taskId);
}
