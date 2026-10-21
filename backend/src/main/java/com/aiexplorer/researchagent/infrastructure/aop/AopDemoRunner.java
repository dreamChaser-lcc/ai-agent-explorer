package com.aiexplorer.researchagent.infrastructure.aop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SP2 实验入口：应用启动完成后自动执行一次 AOP 对比实验。
 * （仿 PersistenceModeDemoRunner 的模式；SP 线教学演示代码，可随时删除。）
 */
@Component
public class AopDemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AopDemoRunner.class);

    private final AopDemoService aopDemoService;

    public AopDemoRunner(AopDemoService aopDemoService) {
        this.aopDemoService = aopDemoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[AOP-实验] 启动后自动执行一次（教学演示）");
        aopDemoService.runDemo();
    }
}