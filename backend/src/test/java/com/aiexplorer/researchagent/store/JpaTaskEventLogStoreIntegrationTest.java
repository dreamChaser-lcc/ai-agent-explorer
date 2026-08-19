package com.aiexplorer.researchagent.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.TaskEventLogEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.store.TaskEventLogStore;
import com.aiexplorer.researchagent.infrastructure.persistence.store.jpa.JpaTaskEventLogStore;
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
 * JPA 模式集成验证：app.persistence.mode=jpa 时，
 * TaskEventLogStore 应装配 JpaTaskEventLogStore 且读写链路正常。
 */
@SpringBootTest(properties = {
        "app.persistence.mode=jpa",
        // 独立内存库，避免与 MyBatis 测试的上下文撞库导致 schema 重复初始化
        "spring.datasource.url=jdbc:h2:mem:research-agent-jpa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("dev")
class JpaTaskEventLogStoreIntegrationTest {

    @Autowired
    private TaskEventLogStore taskEventLogStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void jpaModeShouldAssembleJpaStoreAndReadWriteWorks() {
        // 1. 断言装配的是 JPA 实现
        assertTrue(taskEventLogStore instanceof JpaTaskEventLogStore,
                "app.persistence.mode=jpa 时应装配 JpaTaskEventLogStore");

        // 2. 构造事件（含 JSON payload，验证 @JdbcTypeCode 列映射）
        TaskEventLogEntity eventLogEntity = new TaskEventLogEntity();
        eventLogEntity.setTaskId(UUID.randomUUID());
        eventLogEntity.setEventType(EventType.TASK_CREATED);
        eventLogEntity.setEventMessage("JPA 模式集成测试事件");
        eventLogEntity.setEventPayload(objectMapper.createObjectNode().put("demoKey", "demoValue"));
        eventLogEntity.setOperatorType(OperatorType.SYSTEM);
        eventLogEntity.setOperatorId("integration-test");
        eventLogEntity.setCreatedAt(OffsetDateTime.now());

        // 3. 写入：JPA 的 save() 自动生成主键
        taskEventLogStore.save(eventLogEntity);
        assertNotNull(eventLogEntity.getId(), "JPA 应通过 @GeneratedValue 自动生成主键");

        // 4. 按任务查询并断言内容
        List<TaskEventLogEntity> eventLogs =
                taskEventLogStore.findByTaskIdOrderByCreatedAtDesc(eventLogEntity.getTaskId());
        assertEquals(1, eventLogs.size());
        assertEquals("JPA 模式集成测试事件", eventLogs.get(0).getEventMessage());
        assertEquals("demoValue", eventLogs.get(0).getEventPayload().get("demoKey").asText(),
                "JSON 列应能正确读写");
    }
}
