package com.aiexplorer.researchagent.api.request;

import jakarta.validation.constraints.NotNull;

/**
 * 承载研究计划确认或拒绝的请求参数。
 */
public record PlanConfirmationRequest(
        @NotNull(message = "approved 不能为空") Boolean approved,
        String responseMessage) {
}
