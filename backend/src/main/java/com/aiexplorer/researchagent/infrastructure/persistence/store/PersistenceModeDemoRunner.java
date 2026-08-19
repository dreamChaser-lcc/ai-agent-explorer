package com.aiexplorer.researchagent.infrastructure.persistence.store;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.OperatorType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 持久化模式切换演示：应用启动后自动执行一次"写入 + 查询"，
 * 让 JPA 与 MyBatis 两套实现的 SQL 直接打印在控制台，便于对照学习。
 *
 * <p>仅 dev profile 生效（生产环境不运行），演示数据使用随机 taskId，
 * 不会污染任何真实业务任务。</p>
 */
@Component
@Profile("dev")
public class PersistenceModeDemoRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceModeDemoRunner.class);

    private final TaskEventLogStore taskEventLogStore;

    public PersistenceModeDemoRunner(TaskEventLogStore taskEventLogStore) {
        this.taskEventLogStore = taskEventLogStore;
    }

    @Override
    public void run(String... args) {
        LOGGER.info("========== 持久化模式演示开始 ==========");
        LOGGER.info("当前持久化实现：{}", activeStoreName());

        // 构造一条演示事件（模拟 TaskEventService 的写入流程）
        TaskEventLogEntity demoEvent = new TaskEventLogEntity();
        demoEvent.setTaskId(UUID.randomUUID());
        demoEvent.setEventType(EventType.TASK_CREATED);
        demoEvent.setEventMessage("持久化模式切换演示事件（JPA / MyBatis 对照）");
        demoEvent.setOperatorType(OperatorType.SYSTEM);
        demoEvent.setOperatorId("demo-runner");
        demoEvent.setCreatedAt(OffsetDateTime.now());

        // 执行写入：JPA 走 save()，MyBatis 走手写 INSERT
        taskEventLogStore.save(demoEvent);
        LOGGER.info("已写入演示事件 id={}，taskId={}，上方日志为当前实现的写入 SQL", demoEvent.getId(), demoEvent.getTaskId());

        // 执行查询：验证写入可读回，同时展示查询 SQL
        List<TaskEventLogEntity> demoEvents =
                taskEventLogStore.findByTaskIdOrderByCreatedAtDesc(demoEvent.getTaskId());
        LOGGER.info("按 taskId 查询到 {} 条事件，事件内容：{}", demoEvents.size(), demoEvents.get(0).getEventMessage());
        LOGGER.info("========== 持久化模式演示结束 ==========");
    }

    private String activeStoreName() {
        return taskEventLogStore.getClass().getSimpleName();
    }
}
