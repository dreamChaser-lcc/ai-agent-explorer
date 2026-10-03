package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.api.response.ApiError;
import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一处理接口层常见异常，返回一致的错误结构（ApiError）。
 *
 * 机制说明：本类在应用启动时被 Spring 扫描并注册（@RestControllerAdvice）；
 * 请求处理过程中抛出的异常"冒泡"到 DispatcherServlet 时，会按类型查表派发到这里。
 * 它没有任何业务代码显式调用——是框架通过反射回调的（"值班室"模式）。
 *
 * 分派规则：异常与多个 handler 匹配时，取【最具体】的那个
 * （例：TaskNotFoundException 会命中第 1 个，而不是最后的 Exception 兜底）。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleTaskNotFound(TaskNotFoundException exception, HttpServletRequest request) {
        return ApiError.of("TASK_NOT_FOUND", exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationError(MethodArgumentNotValidException exception, HttpServletRequest request) {
        // ★ 修复：从异常里取出"哪个字段、为什么错"的明细（旧版把这份信息丢掉了，前端拿不到可改信息）
        List<ApiError.FieldViolation> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ApiError.of("VALIDATION_ERROR", "请求参数校验失败", request.getRequestURI(), fields);
    }

    /** ★ 新增：JSON 解析失败 / 枚举等字段取值非法（旧版会漏出 Spring 默认错误结构）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleUnreadableRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ApiError.of("MALFORMED_REQUEST", "请求体格式错误或字段取值不合法", request.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegalState(IllegalStateException exception, HttpServletRequest request) {
        return ApiError.of("INVALID_STATE", exception.getMessage(), request.getRequestURI());
    }

    /** ★ 新增：兜底——未预料异常统一 500；日志留痕、响应不泄露内部细节。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("未处理异常: {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ApiError.of("INTERNAL_ERROR", "服务器内部错误", request.getRequestURI());
    }
}
