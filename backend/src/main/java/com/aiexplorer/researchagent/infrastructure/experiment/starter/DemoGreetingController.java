package com.aiexplorer.researchagent.infrastructure.experiment.starter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SB2 实验：Starter 的示例接口（组件扫描注册，@RestController 标准写法）。
 *
 * <p><b>★ 用 ObjectProvider 而不是强制注入</b>：starter 的自动配置可能被关闭
 * （app.demo-greeting.enabled=false）→ 此时 DemoGreetingService 不存在 →
 * 强制注入会启动失败；用 ObjectProvider 优雅降级（接口返回提示文案）。</p>
 *
 * <p>这也是官方 starter 的常见写法：Web 层不靠自动配置注册，靠组件扫描；
 * 对"可选 Bean"用 ObjectProvider 兼容关闭场景。</p>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoGreetingController {

    private final ObjectProvider<DemoGreetingService> greetingService;

    public DemoGreetingController(ObjectProvider<DemoGreetingService> greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(defaultValue = "world") String name) {
        return greetingService.getIfAvailable() != null
                ? greetingService.getObject().greet(name)
                : "[starter 未启用] app.demo-greeting.enabled=false";
    }
}