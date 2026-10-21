package com.aiexplorer.researchagent.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SP3 教学演示：请求追踪 Filter（Servlet 层，DispatcherServlet 之前）。
 *
 * 只拦 /api 开头的请求（放过 h2-console 等静态资源）。
 * 观察点：它比 Interceptor 更早进入、更晚离开。
 * （SP 线教学演示代码，可随时删除。）
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startMilliseconds = System.currentTimeMillis();
        log.info("[SP3-Filter] 进入：{} {}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("[SP3-Filter] 离开：耗时 {} ms，status={}",
                    System.currentTimeMillis() - startMilliseconds, response.getStatus());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只观察业务接口，避免 h2-console / actuator 干扰
        return !request.getRequestURI().startsWith("/api");
    }
}