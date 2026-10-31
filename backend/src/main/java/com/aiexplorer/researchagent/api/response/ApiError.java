package com.aiexplorer.researchagent.api.response;

import com.aiexplorer.researchagent.shared.enums.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 统一的错误响应契约（全局异常处理使用）。
 *
 * 设计约定（2026-10-03 场景①：统一 API 规范）：
 * - 成功响应保持"裸资源"风格（HTTP 状态码 + 资源本身，不额外包一层）；
 * - 错误响应统一为本结构：code（给程序看）+ message（给人看）
 *   + path / timestamp（排查用）+ fields（仅参数校验错误非空：字段级明细）。
 */
@Schema(description = "统一错误响应体（全局异常处理器产出）")
public record ApiError(
        @Schema(description = "错误码（给程序判断用）", example = "VALIDATION_FAILED")
        String code,

        @Schema(description = "错误信息（给人看）", example = "title 不能为空")
        String message,

        @Schema(description = "请求路径（排查定位用）", example = "/api/tasks")
        String path,

        @Schema(description = "错误发生时间（ISO 8601，含时区）", example = "2026-10-25T17:30:00+08:00")
        OffsetDateTime timestamp,

        @Schema(description = "字段级校验明细（仅参数校验失败时非空）")
        List<FieldViolation> fields,

        @Schema(description = "是否可重试：true 表示客户端可稍后重试（如临时故障）", example = "false")
        boolean retryable) {

    /** 单个字段的校验失败明细（如 field=title、reason=title 不能为空）。 */
    @Schema(description = "单个字段的校验失败明细")
    public record FieldViolation(
            @Schema(description = "字段名", example = "title")
            String field,

            @Schema(description = "失败原因", example = "title 不能为空")
            String reason) {
    }

    /** 便捷构造：无字段明细（大多数错误场景）。 */
    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, OffsetDateTime.now(), List.of(), false);
    }

    /** 便捷构造：带字段明细（参数校验失败场景）。 */
    public static ApiError of(String code, String message, String path, List<FieldViolation> fields) {
        return new ApiError(code, message, path, OffsetDateTime.now(), fields, false);
    }

    /* ---------- T4 规范：错误码枚举入口（2026-10-31） ---------- */

    /**
     * 错误码枚举版：code 与默认消息取自 {@link ErrorCode}。
     *
     * <p>相比 String 版的好处：编译期有类型保障（写错枚举常量直接编译失败）、
     * 错误码清单集中可查、HTTP 状态与语义在枚举里成对定义。</p>
     */
    public static ApiError of(ErrorCode errorCode, String path) {
        return new ApiError(errorCode.code(), errorCode.message(), path, OffsetDateTime.now(), List.of(), false);
    }

    /** 错误码枚举版 + 上下文覆盖消息（在默认消息基础上补充具体原因，如"任务 xxx 不存在"）。 */
    public static ApiError of(ErrorCode errorCode, String message, String path) {
        return new ApiError(errorCode.code(), message, path, OffsetDateTime.now(), List.of(), false);
    }
}
