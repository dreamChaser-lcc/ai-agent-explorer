package com.aiexplorer.researchagent.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SP6 教学演示：启动后打印 sp6.demo 的最终取值，观察配置优先级（可删）。
 */
@Component
public class Sp6DemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Sp6DemoRunner.class);

    private final Sp6DemoProperties sp6DemoProperties;

    public Sp6DemoRunner(Sp6DemoProperties sp6DemoProperties) {
        this.sp6DemoProperties = sp6DemoProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[SP6] name={}，source={}，tags={}",
                sp6DemoProperties.getName(),
                sp6DemoProperties.getSource(),
                sp6DemoProperties.getTags());
    }
}