package com.aiexplorer.researchagent.api.controller.requestPipline;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 请求管线练习 · 第 5 环：异常处理（只作用于 RequestPiplineController）。
 *
 * <p>用 assignableTypes 限定范围——不影响现有全局 ApiExceptionHandler 对其他业务的作用；
 * 想验证"更具体的 advice 优先"，可以在这里故意抛异常看走哪个 handler。</p>
 */
@RestControllerAdvice(assignableTypes = RequestPiplineController.class)
public class RequestPiplineExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        System.out.println("[ExceptionHandler] 捕获 IllegalArgumentException：" + e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "REQUEST_PIPLINE_ERROR");
        body.put("message", e.getMessage());
        body.put("path", request.getRequestURI());
        return body;
    }
}