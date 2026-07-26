package com.aiexplorer.researchagent.api.request;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 承载创建研究任务所需的请求参数。
 */
public record CreateResearchTaskRequest(
        @NotBlank String title,
        @NotBlank String goal,
        @NotNull ExecutionMode executionMode) {
}
