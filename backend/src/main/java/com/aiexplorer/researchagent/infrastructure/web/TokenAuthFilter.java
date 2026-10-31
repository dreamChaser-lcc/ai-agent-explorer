package com.aiexplorer.researchagent.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SP8 教学演示：Token 鉴权 Filter（可删）。
 *
 * 只在 app.demo.token-auth-enabled=true 时启用（默认关闭，不影响现有功能）。
 * 开启方式：启动前设置环境变量 APP_DEMO_TOKEN_AUTH_ENABLED=true。
 *
 * 演示三件事：
 *   ① 401 的正确姿势：写统一 JSON + return（不继续 chain.doFilter）；
 *   ② 白名单：/actuator、/h2-console 放行（否则探针/控制台会被拦）；
 *   ③ @Order(1)：比 BodyCachingFilter(2) 更靠外——先进入、后离开。
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "app.demo.token-auth-enabled", havingValue = "true")
public class TokenAuthFilter extends OncePerRequestFilter {
    /** 演示用固定 token（真实项目应校验 JWT / 会话）。 */
    private static final String VALID_TOKEN = "demo-token";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // SP8 临时探针：打印调用栈（验证完即删）
        log.info("[SP8-Stack] 调用栈：\n{}", java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                .limit(10).map(StackTraceElement::toString)
                .collect(java.util.stream.Collectors.joining("\n  ")));
        String token = request.getHeader("X-Token");
        log.info("[SP8-Token] {} {}，X-Token={}",
                request.getMethod(), request.getRequestURI(), token == null ? "缺失" : "已提供");

        if (!VALID_TOKEN.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或令牌无效（SP8 教学演示）\"}");
            return;   // ★ 不调用 chain.doFilter —— 请求到此为止
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 白名单：运维端点与管理台不参与鉴权
        return uri.startsWith("/actuator") || uri.startsWith("/h2-console");
    }
}