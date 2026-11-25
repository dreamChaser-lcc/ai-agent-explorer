package com.aiexplorer.researchagent.infrastructure.experiment.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SB2 实验：自定义 Starter 的自动配置类（五要素样板）。
 *
 * <pre>
 *   ① @AutoConfiguration             —— 自动配置类（走 imports 清单，不是组件扫描）
 *   ② @ConditionalOnProperty         —— 开关 app.demo-greeting.enabled（默认开）
 *   ③ @EnableConfigurationProperties —— 绑定属性类
 *   ④ @Bean + @ConditionalOnMissingBean —— 提供默认实现，用户覆盖即让位
 *   ⑤ 注册到 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * </pre>
 *
 * <p>验证：启动加 --debug 看 report 里 DemoGreetingAutoConfiguration matched；
 * 访问 /api/demo/greeting?name=Kafka 得到问候语。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.demo-greeting", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DemoGreetingProperties.class)
public class DemoGreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DemoGreetingService demoGreetingService(DemoGreetingProperties properties) {
        return new DemoGreetingService(properties);
    }
}