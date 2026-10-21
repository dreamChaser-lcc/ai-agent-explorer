package com.aiexplorer.starterdemo;

/**
 * SP5 教学演示：mini-starter 提供的服务。
 *
 * 注意：本类【没有】@Component——它只能由自动配置类通过 @Bean 创建。
 * 如果自动配置没生效，这个 bean 在容器里就不存在（Runner 会打印"不存在"）。
 */
public class GreetingService {

    private final GreetingProperties properties;

    public GreetingService(GreetingProperties properties) {
        this.properties = properties;
    }

    /** 生成问候语：前缀 + 名字 + 后缀（前缀来自配置绑定）。 */
    public String greet(String name) {
        return properties.getPrefix() + name + properties.getSuffix();
    }

    /** 标记当前 bean 的来源，用于区分"自动配置"与"用户自定义"。 */
    public String origin() {
        return "auto-configuration";
    }
}