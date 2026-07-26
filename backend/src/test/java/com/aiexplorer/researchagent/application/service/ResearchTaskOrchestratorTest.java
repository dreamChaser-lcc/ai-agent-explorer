package com.aiexplorer.researchagent.application.service;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchStepRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.ResearchTaskRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.SourceDocumentRepository;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.StepExecutionRepository;
import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/**
 * 校验任务编排器在同步和异步模式下的入口行为。
 */
class ResearchTaskOrchestratorTest {

    @Test
    void shouldExecuteImmediatelyWhenTaskIsSync() {
        UUID taskId = UUID.randomUUID();
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setExecutionMode(ExecutionMode.SYNC);

        ResearchTaskRepository researchTaskRepository = mock(ResearchTaskRepository.class);
        when(researchTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        ResearchTaskOrchestrator orchestrator = spy(new ResearchTaskOrchestrator(
                researchTaskRepository,
                mock(ResearchStepRepository.class),
                mock(StepExecutionRepository.class),
                mock(SourceDocumentRepository.class),
                mock(ResearchToolRegistry.class),
                mock(ResearchReportAssemblyService.class),
                mock(TaskEventService.class),
                mock(TaskProgressStreamService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Runnable::run
        ));
        doNothing().when(orchestrator).executeTask(taskId);

        orchestrator.startExecution(taskId);

        verify(orchestrator).executeTask(taskId);
    }

    @Test
    void shouldDelegateToExecutorWhenTaskIsAsync() {
        UUID taskId = UUID.randomUUID();
        ResearchTaskEntity task = new ResearchTaskEntity();
        task.setExecutionMode(ExecutionMode.ASYNC);

        ResearchTaskRepository researchTaskRepository = mock(ResearchTaskRepository.class);
        when(researchTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Executor executor = mock(Executor.class);
        ResearchTaskOrchestrator orchestrator = spy(new ResearchTaskOrchestrator(
                researchTaskRepository,
                mock(ResearchStepRepository.class),
                mock(StepExecutionRepository.class),
                mock(SourceDocumentRepository.class),
                mock(ResearchToolRegistry.class),
                mock(ResearchReportAssemblyService.class),
                mock(TaskEventService.class),
                mock(TaskProgressStreamService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                executor
        ));

        orchestrator.startExecution(taskId);

        verify(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }
}
