package com.aiexplorer.researchagent.api.controller;

import com.aiexplorer.researchagent.shared.exception.TaskNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一处理接口层常见异常，返回一致的错误结构。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleTaskNotFound(TaskNotFoundException exception) {
        return Map.of(
                "code", "TASK_NOT_FOUND",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationError(MethodArgumentNotValidException exception) {
        return Map.of(
                "code", "VALIDATION_ERROR",
                "message", "请求参数校验失败"
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalState(IllegalStateException exception) {
        return Map.of(
                "code", "INVALID_STATE",
                "message", exception.getMessage()
        );
    }
}
