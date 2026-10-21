package com.aiexplorer.researchagent.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SP8 教学演示：请求体日志 Filter（可删）。
 *
 * 演示"改报文必须在 Filter 做"：
 *   用 CachedBodyHttpServletRequest 包装后读 body 打日志，
 *   Controller 的 @RequestBody 仍能拿到完整 JSON（因为包装器提供了可重复读的流）。
 *
 * @Order(2)：数字越大越靠【内】——它比 TokenAuthFilter(1) 更晚进入、更早离开。
 */
@Component
@Order(2)
public class BodyCachingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BodyCachingFilter.class);

    /** 日志里 body 的最大长度（防止大 body 刷爆日志）。 */
    private static final int MAX_LOG_LENGTH = 160;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 只关心带请求体的方法
        boolean hasBody = "POST".equalsIgnoreCase(request.getMethod())
                || "PUT".equalsIgnoreCase(request.getMethod())
                || "PATCH".equalsIgnoreCase(request.getMethod());
        if (!hasBody) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        log.info("[SP8-Body] 请求体（{} {}）：{}",
                request.getMethod(), request.getRequestURI(), truncate(wrappedRequest.getBodyText()));

        // ★ 必须把包装后的 request 传下去，Controller 才能读到 body
        filterChain.doFilter(wrappedRequest, response);
    }

    private String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return "(空)";
        }
        return text.length() <= MAX_LOG_LENGTH ? text : text.substring(0, MAX_LOG_LENGTH) + "...";
    }
}