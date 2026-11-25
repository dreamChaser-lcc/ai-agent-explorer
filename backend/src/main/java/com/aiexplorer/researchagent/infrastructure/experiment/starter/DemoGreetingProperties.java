package com.aiexplorer.researchagent.infrastructure.experiment.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SB2 实验：Starter 的属性类（对应 application.yml 的 app.demo-greeting.*）。
 *
 * <p>record + @ConfigurationProperties（Spring Boot 3 支持构造器绑定）；
 * 默认值在紧凑构造器里兜底。</p>
 */
@ConfigurationProperties(prefix = "app.demo-greeting")
public record DemoGreetingProperties(String prefix, String suffix) {

    public DemoGreetingProperties {
        if (prefix == null) {
            prefix = "Hello, ";
        }
        if (suffix == null) {
            suffix = "!";
        }
    }
}