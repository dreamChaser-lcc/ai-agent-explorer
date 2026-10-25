package com.aiexplorer.researchagent.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T3：API 文档元信息（SpringDoc OpenAPI 3）。
 *
 * <p>不加这个 bean 也能生成文档，但标题是默认的 "OpenAPI definition" / version "v0"。
 * 这里显式覆盖成项目名与版本，让 /swagger-ui.html 更像"正式文档"。</p>
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI researchAgentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Research Agent API")
                .version("1.0.0")
                .description("研究任务编排系统接口文档（SpringDoc 2.8.6 自动生成）"));
    }
}