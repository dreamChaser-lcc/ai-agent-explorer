package com.aiexplorer.researchagent.api.response;

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
public record ApiError(
        String code,
        String message,
        String path,
        OffsetDateTime timestamp,
        List<FieldViolation> fields) {

    /** 单个字段的校验失败明细（如 field=title、reason=title 不能为空）。 */
    public record FieldViolation(String field, String reason) {
    }

    /** 便捷构造：无字段明细（大多数错误场景）。 */
    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, OffsetDateTime.now(), List.of());
    }

    /** 便捷构造：带字段明细（参数校验失败场景）。 */
    public static ApiError of(String code, String message, String path, List<FieldViolation> fields) {
        return new ApiError(code, message, path, OffsetDateTime.now(), fields);
    }
}
