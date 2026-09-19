package com.microlab.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置演示接口：读取 Nacos 配置中心的配置项（第 5 站）。
 *
 * <p>{@code @RefreshScope}：动态刷新作用域——Nacos 上的配置变更被感知后，
 * Spring 会重建这个 Bean，使下面的 {@code @Value} 拿到最新值，全程无需重启服务。</p>
 */
@RefreshScope
@RestController
@RequestMapping("/users/config")
public class UserConfigController {

    /**
     * 欢迎语：值来自 Nacos 配置中心的 user-service.yaml。
     * {@code ${microlab.welcome-message:兜底值}} —— 冒号后是"读不到配置时的默认值"。
     */
    @Value("${microlab.welcome-message:默认欢迎语（未从 Nacos 读到配置时的兜底）}")
    private String welcomeMessage;

    /**
     * 查看当前的欢迎语配置。
     *
     * @return 配置中心里的欢迎语
     */
    @GetMapping("/welcome")
    public String queryWelcomeMessage() {
        return welcomeMessage;
    }
}
