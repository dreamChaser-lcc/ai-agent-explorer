package com.aiexplorer.researchagent.infrastructure.web.requestPipline;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 请求管线练习：把 RequestPiplineInterceptor 注册到练习端点上。
 *
 * <p>只挂 /api/request-pipline/**，不影响现有业务路径的拦截器。</p>
 */
@Configuration
public class RequestPiplineWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestPiplineInterceptor())
                .addPathPatterns("/api/request-pipline/**");
    }
}