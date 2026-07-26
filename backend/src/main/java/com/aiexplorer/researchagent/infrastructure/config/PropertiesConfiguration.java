package com.aiexplorer.researchagent.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用应用所需的强类型配置属性绑定。
 */
@Configuration
@EnableConfigurationProperties({
        ExecutionProperties.class,
        LlmProperties.class
})
public class PropertiesConfiguration {
}
