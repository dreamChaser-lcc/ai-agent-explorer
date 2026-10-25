package com.aiexplorer.researchagent.api.request;

import com.aiexplorer.researchagent.shared.enums.ExecutionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 承载创建研究任务所需的请求参数。
 *
 * <p>场景④ 起支持【幂等键】：客户端为"这一次创建请求"生成一个唯一串，
 * 双击/超时重试时【带同一个键】——服务端据此识别"这是同一件事"，不重复创建。</p>
 */
public record CreateResearchTaskRequest(
        @Schema(description = "任务标题", example = "调研 Java Agent 架构",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "title 不能为空") String title,

        @Schema(description = "研究目标（自然语言描述）", example = "梳理可落地的 DDD 切入点",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "goal 不能为空") String goal,

        @Schema(description = "执行模式：SYNC 同步返回结果 / ASYNC 走 Kafka 异步执行",
                example = "ASYNC", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "executionMode 不能为空") ExecutionMode executionMode,

        /**
         * 幂等键（可选）：
         *   - 传了：重复提交（同键）不会重复创建——重放首次结果；
         *   - 不传（null）：普通请求，不参与幂等——
         *     判断"是否重复"的权力在调用方，服务端不替它猜（用户真心要建两个同名任务也是合法的）。
         */
        @Schema(description = "幂等键（可选）：相同 key 重复提交只创建一次，重放首次结果",
                example = "order-20261025-001")
        @Size(max = 128, message = "idempotencyKey 最长 128 字符") String idempotencyKey) {
}
