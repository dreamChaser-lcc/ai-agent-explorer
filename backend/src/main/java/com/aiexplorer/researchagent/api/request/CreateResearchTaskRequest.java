package com.aiexplorer.researchagent.api.request;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 承载创建研究任务所需的请求参数。
 */
public record CreateResearchTaskRequest(
        @NotBlank(message = "title 不能为空") String title,
        @NotBlank(message = "goal 不能为空") String goal,
        @NotNull(message = "executionMode 不能为空") ExecutionMode executionMode) {
}
