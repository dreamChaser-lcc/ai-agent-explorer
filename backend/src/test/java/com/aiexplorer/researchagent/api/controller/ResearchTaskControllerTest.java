package com.aiexplorer.researchagent.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexplorer.researchagent.api.request.CreateResearchTaskRequest;
import com.aiexplorer.researchagent.api.response.TaskDetailResponse;
import com.aiexplorer.researchagent.api.response.TaskSummaryResponse;
import com.aiexplorer.researchagent.application.service.ResearchTaskCommandService;
import com.aiexplorer.researchagent.application.service.ResearchTaskControlService;
import com.aiexplorer.researchagent.application.service.ResearchTaskQueryService;
import com.aiexplorer.researchagent.application.service.TaskActivityQueryService;
import com.aiexplorer.researchagent.application.service.TaskProgressStreamService;
import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import com.aiexplorer.researchagent.shared.enums.TaskStage;
import com.aiexplorer.researchagent.shared.enums.TaskStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 校验研究任务控制器的主要接口行为。
 */
@WebMvcTest(controllers = ResearchTaskController.class)
@Import(ApiExceptionHandler.class)
class ResearchTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResearchTaskCommandService researchTaskCommandService;

    @MockBean
    private ResearchTaskQueryService researchTaskQueryService;

    @MockBean
    private ResearchTaskControlService researchTaskControlService;

    @MockBean
    private TaskActivityQueryService taskActivityQueryService;

    @MockBean
    private TaskProgressStreamService taskProgressStreamService;

    @Test
    void shouldCreateTaskSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskSummaryResponse response = new TaskSummaryResponse(
                taskId,
                "TASK-12345678",
                "测试任务",
                ExecutionMode.ASYNC,
                TaskStatus.WAITING_FOR_CONFIRMATION,
                TaskStage.PLANNING,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(researchTaskCommandService.createTask(any(CreateResearchTaskRequest.class), eq("demo-user")))
                .thenReturn(response);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "测试任务",
                                  "goal": "调研 Java 技术栈下的 Research Agent 架构",
                                  "executionMode": "ASYNC"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_CONFIRMATION"));
    }

    @Test
    void shouldReturnTaskListSuccessfully() throws Exception {
        TaskSummaryResponse response = new TaskSummaryResponse(
                UUID.randomUUID(),
                "TASK-87654321",
                "列表任务",
                ExecutionMode.SYNC,
                TaskStatus.RUNNING,
                TaskStage.EXECUTING,
                false,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(researchTaskQueryService.listTasks()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("列表任务"))
                .andExpect(jsonPath("$[0].executionMode").value("SYNC"));
    }

    @Test
    void shouldReturnTaskDetailSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskDetailResponse detailResponse = new TaskDetailResponse(
                taskId,
                "TASK-00000001",
                "详情任务",
                "调研 SSE 方案",
                ExecutionMode.ASYNC,
                TaskStatus.WAITING_FOR_CONFIRMATION,
                TaskStage.PLANNING,
                true,
                "等待确认的研究计划",
                List.of("搜索候选资料", "抓取资料正文"),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(researchTaskQueryService.getTaskDetail(taskId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestPlanSummary").value("等待确认的研究计划"))
                .andExpect(jsonPath("$.plannedSteps[0]").value("搜索候选资料"));
    }

    @Test
    void shouldConfirmPlanSuccessfully() throws Exception {
        UUID taskId = UUID.randomUUID();
        doNothing().when(researchTaskControlService).confirmPlan(taskId, true, "继续执行");

        mockMvc.perform(post("/api/tasks/{taskId}/confirm", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "responseMessage": "继续执行"
                                }
                                """))
                .andExpect(status().isNoContent());
    }
}
