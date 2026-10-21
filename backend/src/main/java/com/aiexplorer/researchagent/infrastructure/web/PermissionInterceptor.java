package com.aiexplorer.researchagent.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * SP8 教学演示：方法级权限拦截器（可删）。
 *
 * 演示 Interceptor 的独特能力：**通过 HandlerMethod 读到目标方法上的注解**。
 *   - Filter 只能拿到 URL，不知道最终落到哪个方法；
 *   - Interceptor 的 handler 参数是 HandlerMethod，能拿到方法、注解、甚至参数。
 *
 * 两种拒绝方式（讲义 §四·补 会对比）：
 *   ① 返回 false + 手动写响应（本类采用，简单直观）；
 *   ② 抛异常 → 交给 @RestControllerAdvice（能复用统一错误体）。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 静态资源等非方法处理器直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            return true;
        }

        String role = request.getHeader("X-Role");
        log.info("[SP8-Permission] {}.{} 需要角色={}，请求头 X-Role={}",
                handlerMethod.getBeanType().getSimpleName(),
                handlerMethod.getMethod().getName(),
                requireRole.value(),
                role == null ? "缺失" : role);

        if (!requireRole.value().equals(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"权限不足（SP8 教学演示）\"}");
            return false;   // ★ 终止请求：Controller 不会执行
        }
        return true;
    }
}