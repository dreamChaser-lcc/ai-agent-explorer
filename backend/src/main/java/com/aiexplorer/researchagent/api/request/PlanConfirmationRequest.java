package com.aiexplorer.researchagent.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 承载研究计划确认或拒绝的请求参数。
 */
public record PlanConfirmationRequest(
        @Schema(description = "是否批准计划：true 批准并开始执行 / false 拒绝",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "approved 不能为空") Boolean approved,

        @Schema(description = "给计划的反馈说明（拒绝时建议填写原因）", example = "继续执行")
        String responseMessage) {
}
