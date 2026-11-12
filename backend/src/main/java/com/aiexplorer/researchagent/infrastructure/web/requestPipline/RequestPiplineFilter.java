package com.aiexplorer.researchagent.infrastructure.web.requestPipline;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * 请求管线练习 · 第 2 环：Filter（Servlet 规范，在 DispatcherServlet【之外】）。
 *
 * <p><b>★ 本类是「注册方式 2」的代码实例：FilterRegistrationBean 手动注册</b>
 * （见 {@link RequestPiplineFilterConfig}）。对比项目里另外三个 Filter：</p>
 *
 * <table border="1">
 *   <caption>两种注册方式对照</caption>
 *   <tr><th></th><th>方式 1：@Component + @Order</th><th>方式 2：FilterRegistrationBean（本类）</th></tr>
 *   <tr><td>注册范围</td><td>全部 /*（不可配）</td><td>可精确 URL（本类：/api/request-pipline/*）</td></tr>
 *   <tr><td>顺序</td><td>@Order</td><td>setOrder</td></tr>
 *   <tr><td>判断路径</td><td>需在 doFilter 里判断 URI</td><td>容器按 URL 模式匹配，无需判断</td></tr>
 *   <tr><td>项目实例</td><td>TokenAuthFilter / BodyCachingFilter / RequestTraceFilter</td><td>本类</td></tr>
 * </table>
 *
 * <p><b>⚠ 冲突坑</b>：本类【故意】不加 @Component——若同时被组件扫描和
 * FilterRegistrationBean 注册，会被注册两次、每个请求执行两遍。</p>
 *
 * <p>位置提醒：doFilter 前后把整个 Spring MVC 包在中间——所以 Filter 日志一定
 * 【最先】和【最后】出现。</p>
 */
public class RequestPiplineFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        System.out.println("[Filter]   >>> 进入 doFilter（DispatcherServlet 之前）");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        System.out.println("[CORS-Debug] Origin=" + httpRequest.getHeader("Origin")
        + " | UA=" + httpRequest.getHeader("User-Agent")
        + " | RemoteAddr=" + httpRequest.getRemoteAddr());

        try {
            chain.doFilter(request, response);   // 放行：后面才会走到 DispatcherServlet
        } finally {
            System.out.println("[Filter]   <<< 退出 doFilter（耗时 "
                    + (System.currentTimeMillis() - start) + "ms）");
        }
    }
}