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

    /**
     * 跳过 H2 Console（2026-10-26 修复）：
     *
     * <p>H2 Console 的登录表单是 application/x-www-form-urlencoded，它依赖容器的
     * {@code getParameter("url")} 解析表单。但 Tomcat 的规则是【流一旦被读取就不再解析参数】——
     * CachedBodyHttpServletRequest 只重写了 getInputStream()，没有重写 getParameter 系列，
     * 于是 H2 Console 拿到的 url 是空字符串 → 报
     * "No suitable driver found for "（URL 为空，2026-10-25/26 实测反复踩坑）。</p>
     *
     * <p>H2 Console 是框架页面而非业务接口，直接跳过最安全；业务接口继续走缓存包装。</p>
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/h2-console");
    }

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
