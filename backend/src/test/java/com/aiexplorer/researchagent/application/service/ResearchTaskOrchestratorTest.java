package com.aiexplorer.researchagent.application.service;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexplorer.researchagent.infrastructure.lock.DistributedTaskLock;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.SourceDocumentRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.StepExecutionRepository;
import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 校验任务编排器在同步和异步模式下的入口行为。
 *
 * 场景⑫ 修复：场景⑥ 把第 10 个参数从 Executor 换成 TaskExecutionProducer、
 * 场景⑨ 又追加 DistributedTaskLock —— 测试必须同步跟上，否则 testCompile 直接失败。
 */
class ResearchTaskOrchestratorTest {

    /** 同步模式：直接在调用线程执行（用 spy 拦住真实执行）。 */
    @Test
    void shouldExecuteImmediatelyWhenTaskIsSync() {
        UUID taskId = UUID.randomUUID();
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setExecutionMode(ExecutionMode.SYNC);

        ResearchTaskOrchestrator orchestrator = spy(newOrchestrator(taskId, task, mock(TaskExecutionProducer.class)));
        doNothing().when(orchestrator).executeTask(taskId);

        orchestrator.startExecution(taskId);

        verify(orchestrator).executeTask(taskId);
    }

    /** 异步模式：只发 Kafka 消息（不再走线程池）。 */
    @Test
    void shouldPublishMessageWhenTaskIsAsync() {
        UUID taskId = UUID.randomUUID();
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setExecutionMode(ExecutionMode.ASYNC);

        TaskExecutionProducer producer = mock(TaskExecutionProducer.class);
        ResearchTaskOrchestrator orchestrator = newOrchestrator(taskId, task, producer);

        orchestrator.startExecution(taskId);

        verify(producer).publish(taskId);
    }

    private ResearchTaskOrchestrator newOrchestrator(
            UUID taskId, ResearchTaskEntity task, TaskExecutionProducer producer) {
        ResearchTaskRepository taskRepository = mock(ResearchTaskRepository.class);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        return new ResearchTaskOrchestrator(
                taskRepository,
                mock(ResearchStepRepository.class),
                mock(StepExecutionRepository.class),
                mock(SourceDocumentRepository.class),
                mock(ResearchToolRegistry.class),
                mock(ResearchReportAssemblyService.class),
                mock(TaskEventService.class),
                mock(TaskProgressStreamService.class),
                new ObjectMapper(),
                producer,
                mock(DistributedTaskLock.class));
    }
}