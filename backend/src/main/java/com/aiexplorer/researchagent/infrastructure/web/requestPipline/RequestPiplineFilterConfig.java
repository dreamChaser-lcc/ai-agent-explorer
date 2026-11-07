package com.aiexplorer.researchagent.infrastructure.web.requestPipline;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 请求管线练习 · ★ 注册方式 2 的代码实例：FilterRegistrationBean 手动注册。
 *
 * <p>与方式 1（@Component + @Order，见 TokenAuthFilter 等）的区别：</p>
 * <ul>
 *   <li>能精确指定 URL 模式：{@code addUrlPatterns("/api/request-pipline/*")}
 *       —— Filter 里不用再判断 URI；</li>
 *   <li>能命名（setName）：日志/排查时能对上号；</li>
 *   <li>能配初始化参数（setInitParameters）；</li>
 *   <li>可条件化（把 @Bean 包在 @ConditionalOnProperty 里即可开关）。</li>
 * </ul>
 *
 * <p><b>⚠ 坑</b>：配套的 RequestPiplineFilter 类上【不能】加 @Component，
 * 否则同一 Filter 会被注册两次、每个请求执行两遍。</p>
 */
@Configuration
public class RequestPiplineFilterConfig {

    @Bean
    public FilterRegistrationBean<RequestPiplineFilter> requestPiplineFilter() {
        FilterRegistrationBean<RequestPiplineFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestPiplineFilter());
        bean.addUrlPatterns("/api/request-pipline/*");   // 精确 URL：只拦练习端点
        bean.setOrder(3);                                // 排在 TokenAuth(1)/BodyCaching(2) 之后
        bean.setName("requestPiplineFilter");            // 命名：便于日志/排查
        return bean;
    }
}