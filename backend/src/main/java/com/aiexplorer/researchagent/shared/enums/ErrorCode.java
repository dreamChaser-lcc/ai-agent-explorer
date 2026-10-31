package com.aiexplorer.researchagent.shared.enums;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码表（T4 规范落地：错误码枚举化）。
 *
 * <p>设计三要素：</p>
 * <ul>
 *   <li>{@code code}：对外契约字符串——<b>保持不变</b>（前端已按这些值判断，改名 = 破坏契约）</li>
 *   <li>{@code message}：默认人类可读消息（可被具体 handler 覆盖为更精确的上下文）</li>
 *   <li>{@code httpStatus}：与之匹配的 HTTP 状态码（集中在此映射，handler 不再各自散写）</li>
 * </ul>
 *
 * <p>为什么要有这张表（对照散落字符串的痛点）：</p>
 * <pre>
 * 之前：ApiError.of("TASK_NOT_FOUND", ...) —— 字符串散落 11 处
 *   · 拼写错误编译期查不出（"TASK_NOT_FOND" 只有运行时才炸）
 *   · 没有一处能看全"项目到底有哪些错误码"
 *   · HTTP 状态码在 handler 里各自与 code 配对，容易配错
 *
 * 现在：ErrorCode.TASK_NOT_FOUND -> code/message/httpStatus 三合一
 *   · 编译期有类型保障（写错枚举常量直接编译失败）
 *   · 一张表看全（本类就是错误码清单）
 *   · 状态码与语义绑定（TASK_NOT_FOUND 天生 404）
 * </pre>
 *
 * <p>渐进式迁移约定（与 T1 Lombok 的决策一致）：现有 String 版 {@code ApiError.of}
 * 保留兼容；新写/迭代的 handler 优先用 {@code ApiError.of(ErrorCode)}。</p>
 */
public enum ErrorCode {

    /* ---------- 参数与请求（4xx 客户端错误） ---------- */

    /** 参数校验失败（Bean Validation，含字段级明细）。 */
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数校验失败", HttpStatus.BAD_REQUEST),

    /** 参数非法（非校验注解能覆盖的业务规则，如枚举值越界）。 */
    INVALID_PARAMETER("INVALID_PARAMETER", "请求参数非法", HttpStatus.BAD_REQUEST),

    /** 报文格式错误（JSON 解析失败、类型不匹配）。 */
    MALFORMED_REQUEST("MALFORMED_REQUEST", "请求报文格式错误", HttpStatus.BAD_REQUEST),

    /** 请求方法不支持（如对只读接口发 POST）。 */
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),

    /** 媒体类型不支持（Content-Type 不是 JSON）。 */
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "不支持的媒体类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /* ---------- 资源与状态 ---------- */

    /** 通用 404（路径不存在、资源不存在）。 */
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),

    /** 任务不存在（业务级 404，前端可提示"任务已删除"）。 */
    TASK_NOT_FOUND("TASK_NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND),

    /** 业务状态非法（如对已完成的报告请求确认）。 */
    INVALID_STATE("INVALID_STATE", "当前状态不允许此操作", HttpStatus.CONFLICT),

    /** 并发冲突（乐观锁/分布式锁，前端提示"请刷新重试"）。 */
    CONCURRENT_CONFLICT("CONCURRENT_CONFLICT", "数据已被其他人修改，请刷新重试", HttpStatus.CONFLICT),

    /* ---------- 数据与系统 ---------- */

    /** 数据访问失败（@Repository 翻译后的 Spring DAO 异常，见 J4 课）。 */
    DATA_ACCESS_ERROR("DATA_ACCESS_ERROR", "数据访问失败", HttpStatus.BAD_REQUEST),

    /** 未预期异常（兜底 500）。 */
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * 按对外 code 反查枚举（供测试/前端对齐用）。
     *
     * @throws IllegalArgumentException 未知 code（说明调用方传了表外的值）
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知错误码: " + code);
    }
}
