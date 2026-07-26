package com.aiexplorer.researchagent.api.request;

import jakarta.validation.constraints.NotNull;

/**
 * 承载研究计划确认或拒绝的请求参数。
 */
public record PlanConfirmationRequest(
        @NotNull Boolean approved,
        String responseMessage) {
}
