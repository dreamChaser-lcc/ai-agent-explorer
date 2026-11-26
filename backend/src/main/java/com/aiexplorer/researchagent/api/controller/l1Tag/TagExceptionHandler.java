package com.aiexplorer.researchagent.api.controller.l1Tag;

import com.aiexplorer.researchagent.api.response.ApiError;
import com.aiexplorer.researchagent.shared.enums.ErrorCode;
import com.aiexplorer.researchagent.shared.exception.TagNameDuplicateException;
import com.aiexplorer.researchagent.shared.exception.TagNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 标签域专用异常处理器（只作用于 {@link TagController}）。
 *
 * <p><b>为什么单独写，而不是全塞进全局 ApiExceptionHandler：</b></p>
 * <ol>
 *   <li>域内异常内聚：标签的错误码/日志只在标签域维护，不污染全局异常表；</li>
 *   <li>作用范围精确：{@code @RestControllerAdvice(assignableTypes = TagController.class)}
 *       只拦截 TagController 抛出的异常，其他 Controller 完全不受影响；</li>
 *   <li>★ 必须加 {@code @Order(Ordered.HIGHEST_PRECEDENCE)}：
 *       Spring 的异常解析按 Advice 顺序找 handler，全局 ApiExceptionHandler 里有
 *       {@code @ExceptionHandler(Exception.class)} 兜底——若本类不排在最前面，
 *       标签异常会被兜底抢走（返回 500 而不是 404/409）。</li>
 * </ol>
 *
 * <p><b>注意</b>：参数校验失败（@Valid）不在这里处理——它由全局
 * {@code ApiExceptionHandler} 的 MethodArgumentNotValidException 分支统一返回 400 + 字段明细。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TagController.class)
public class TagExceptionHandler {

    /** 标签不存在 → 404。 */
    @ExceptionHandler(TagNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleTagNotFound(TagNotFoundException exception, HttpServletRequest request) {
        log.warn("[Tag] 标签不存在 id={} path={}", exception.getId(), request.getRequestURI());
        return ApiError.of(ErrorCode.TAG_NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    /** 标签名重复 → 409。 */
    @ExceptionHandler(TagNameDuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleTagNameDuplicate(TagNameDuplicateException exception, HttpServletRequest request) {
        log.warn("[Tag] 标签名重复 name={} path={}", exception.getName(), request.getRequestURI());
        return ApiError.of(ErrorCode.TAG_NAME_DUPLICATE, exception.getMessage(), request.getRequestURI());
    }
}
