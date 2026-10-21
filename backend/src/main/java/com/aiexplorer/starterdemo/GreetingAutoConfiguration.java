package com.aiexplorer.starterdemo;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SP5 教学演示：mini-starter 的自动配置类（本课主角）。
 *
 * 三个关键注解各司其职：
 *   @AutoConfiguration          —— 声明"这是自动配置"（等价于 @Configuration + 一些顺序约束）
 *   @EnableConfigurationProperties —— 把 GreetingProperties 绑定成 bean（app.greeting.* → 对象）
 *   @ConditionalOnMissingBean   —— 用户自己定义了 GreetingService 就退让（★ 第三幕验证）
 *
 * 这个类能被加载，唯一途径是 META-INF/spring/...AutoConfiguration.imports 里列出它
 * ——它所在的包不在应用的 @ComponentScan 范围内。
 */
@AutoConfiguration
@EnableConfigurationProperties(GreetingProperties.class)
public class GreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GreetingService greetingService(GreetingProperties properties) {
        return new GreetingService(properties);
    }
}