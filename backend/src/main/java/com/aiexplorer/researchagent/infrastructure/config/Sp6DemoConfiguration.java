package com.aiexplorer.researchagent.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SP6 教学演示：启用 Sp6DemoProperties 的配置绑定（可删）。
 */
@Configuration
@EnableConfigurationProperties(Sp6DemoProperties.class)
public class Sp6DemoConfiguration {
}