package com.aiexplorer.researchagent.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * SP3 教学演示：请求追踪 Interceptor（Spring MVC 层，DispatcherServlet 之内）。
 *
 * 三个时机：
 *   preHandle        —— 进入 Controller 之前（返回 false 可拦截请求）
 *   postHandle       —— Controller 正常返回之后（异常时不执行）
 *   afterCompletion  —— 请求完成时（无论成功失败都执行，可拿到异常）
 *
 * 与 Filter 的关键差别：这里的 handler 参数能告诉你要执行【哪个 Controller 方法】。
 * （SP 线教学演示代码，可随时删除。）
 */
@Component
public class RequestTraceInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String handlerText = handler instanceof HandlerMethod handlerMethod
                ? handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName()
                : String.valueOf(handler);
        log.info("[SP3-Interceptor] preHandle：handler = {}", handlerText);
        return true;   // false 则请求在此被拦断（Controller 不会执行）
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, org.springframework.web.servlet.ModelAndView modelAndView) {
        log.info("[SP3-Interceptor] postHandle（Controller 已正常返回，视图渲染前）");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        log.info("[SP3-Interceptor] afterCompletion（请求完成，无论成败；异常={}）",
                exception == null ? "无" : exception.getMessage());
    }
}