package com.aiexplorer.researchagent.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.store.TaskEventLogStore;
import com.aiexplorer.researchagent.infrastructure.persistence.store.mybatis.MyBatisTaskEventLogStore;
import com.aiexplorer.researchagent.shared.enums.EventType;
import com.aiexplorer.researchagent.shared.enums.OperatorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * MyBatis 模式集成验证：app.persistence.mode=mybatis 时，
 * TaskEventLogStore 应装配 MyBatisTaskEventLogStore 且读写链路正常。
 */
@SpringBootTest(properties = {
        "app.persistence.mode=mybatis",
        // 独立内存库，避免与 JPA 测试的上下文撞库导致 schema 重复初始化
        "spring.datasource.url=jdbc:h2:mem:research-agent-mybatis;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("dev")
class MyBatisTaskEventLogStoreIntegrationTest {

    @Autowired
    private TaskEventLogStore taskEventLogStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void myBatisModeShouldAssembleMyBatisStoreAndReadWriteWorks() {
        // 1. 断言装配的是 MyBatis 实现
        assertTrue(taskEventLogStore instanceof MyBatisTaskEventLogStore,
                "app.persistence.mode=mybatis 时应装配 MyBatisTaskEventLogStore");

        // 2. 构造事件（含 JSON payload，验证自定义 JsonNodeTypeHandler 映射）
        TaskEventLogEntity eventLogEntity = new TaskEventLogEntity();
        eventLogEntity.setTaskId(UUID.randomUUID());
        eventLogEntity.setEventType(EventType.PLAN_GENERATED);
        eventLogEntity.setEventMessage("MyBatis 模式集成测试事件");
        eventLogEntity.setEventPayload(objectMapper.createObjectNode().put("demoKey", "myBatisValue"));
        eventLogEntity.setOperatorType(OperatorType.SYSTEM);
        eventLogEntity.setOperatorId("integration-test");
        eventLogEntity.setCreatedAt(OffsetDateTime.now());

        // 3. 写入：MyBatis 无 @GeneratedValue，由实现内手动生成主键
        taskEventLogStore.save(eventLogEntity);
        assertNotNull(eventLogEntity.getId(), "MyBatis 实现应在 save 时手动补齐主键");

        // 4. 按任务查询并断言内容
        List<TaskEventLogEntity> eventLogs =
                taskEventLogStore.findByTaskIdOrderByCreatedAtDesc(eventLogEntity.getTaskId());
        assertEquals(1, eventLogs.size());
        assertEquals("MyBatis 模式集成测试事件", eventLogs.get(0).getEventMessage());
        assertEquals("myBatisValue", eventLogs.get(0).getEventPayload().get("demoKey").asText(),
                "自定义 TypeHandler 应能正确读写 JSON 列");
    }
}
