package com.aiexplorer.researchagent.infrastructure.experiment.starter;

/**
 * SB2 实验：Starter 提供的服务（由自动配置类装配，不是 @Component 扫描来的）。
 */
public class DemoGreetingService {

    private final DemoGreetingProperties properties;

    public DemoGreetingService(DemoGreetingProperties properties) {
        this.properties = properties;
    }

    public String greet(String name) {
        return properties.prefix() + name + properties.suffix();
    }
}