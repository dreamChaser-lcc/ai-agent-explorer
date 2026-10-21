package com.aiexplorer.researchagent.infrastructure.starterdemo;

import com.aiexplorer.starterdemo.GreetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SP5 教学演示：验证 mini-starter 是否生效（启动后自动打印）。可随时删除。
 *
 * 关键点：本类在应用的 @ComponentScan 范围内（com.aiexplorer.researchagent...），
 * 它依赖的 GreetingService 在包外（com.aiexplorer.starterdemo）——
 * 如果 GreetingService 能被注入，说明自动配置确实把它装配进来了。
 *
 * 用 ObjectProvider 而不是直接注入：starter 不生效时不至于让应用启动失败（能优雅打印"不存在"）。
 */
@Component
public class GreetingDemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GreetingDemoRunner.class);

    private final ObjectProvider<GreetingService> greetingServiceProvider;

    public GreetingDemoRunner(ObjectProvider<GreetingService> greetingServiceProvider) {
        this.greetingServiceProvider = greetingServiceProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        GreetingService greetingService = greetingServiceProvider.getIfAvailable();
        if (greetingService == null) {
            log.warn("[SP5] GreetingService 不存在 → 自动配置未生效（检查 imports 清单/条件注解）");
            return;
        }
        log.info("[SP5] GreetingService 生效：{}（来源={}）",
                greetingService.greet("Spring"), greetingService.origin());
    }
}