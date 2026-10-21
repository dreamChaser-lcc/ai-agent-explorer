package com.aiexplorer.researchagent.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SP3 教学演示：注册请求追踪 Interceptor（教学演示，可随时删除）。
 *
 * 注意：项目已有 WebCorsConfiguration（同样 implements WebMvcConfigurer）——
 * 多个 WebMvcConfigurer 会【叠加生效】（Spring 收集所有实现逐个调用），不会互相覆盖。
 */
@Configuration
public class WebRequestTraceConfiguration implements WebMvcConfigurer {

    private final RequestTraceInterceptor requestTraceInterceptor;

    public WebRequestTraceConfiguration(RequestTraceInterceptor requestTraceInterceptor) {
        this.requestTraceInterceptor = requestTraceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTraceInterceptor).addPathPatterns("/api/**");
    }
}