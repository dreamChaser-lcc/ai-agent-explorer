package com.aiexplorer.researchagent.infrastructure.web.requestPipline;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 请求管线练习 · 第 3 环：Interceptor（DispatcherServlet 内部，AOP 之外）。
 *
 * <p>三个钩子的执行顺序（本练习要亲眼验证的）：
 * <pre>
 *   preHandle（正序进）
 *     → AOP → Controller → Service
 *   postHandle（Controller 返回后，视图渲染前；逆序出）
 *   afterCompletion（请求彻底结束；逆序出，无论成功失败必执行）
 * </pre>
 */
public class RequestPiplineInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        System.out.println("[Interceptor] >>> preHandle（进入 Controller 之前）");
        return true;   // true = 放行；false = 中断（后续 Controller 不执行）
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler, ModelAndView modelAndView) {
        System.out.println("[Interceptor] <<< postHandle（Controller 已返回，视图渲染之前）");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        System.out.println("[Interceptor] <<< afterCompletion（请求结束，必执行；异常="
                + (ex == null ? "无" : ex.getClass().getSimpleName()) + "）");
    }
}