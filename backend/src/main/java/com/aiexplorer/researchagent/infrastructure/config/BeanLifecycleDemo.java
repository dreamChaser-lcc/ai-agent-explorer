package com.aiexplorer.researchagent.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SP1 教学演示：Bean 生命周期（可随时删除）。
 *
 * 观察方法：
 *   启动应用 → 看日志 ① 和 ③；Ctrl+C 正常关闭 → 看日志 ⑤。
 *
 * 注意：字段 applicationName 用 @Value 注入，它赋值发生在"构造器之后、@PostConstruct 之前"——
 * 所以 ① 里不能读它（是 null），③ 里才能读。这正是生命周期顺序的意义。
 */
@Component
public class BeanLifecycleDemo {

    private static final Logger log = LoggerFactory.getLogger(BeanLifecycleDemo.class);

    private final ObjectMapper objectMapper;   // 构造器注入：实例化阶段就绪

    @Value("${spring.application.name:research-agent-backend}")
    private String applicationName;            // 字段注入：属性注入阶段才赋值

    public BeanLifecycleDemo(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("[SP1-生命周期] ① 实例化：构造器执行（此刻 applicationName = {}）", applicationName);
    }

    @PostConstruct
    public void onInitialized() {
        log.info("[SP1-生命周期] ③ 初始化完成：@PostConstruct（此刻 applicationName = {}）", applicationName);
    }

    @PreDestroy
    public void onBeforeDestroy() {
        log.info("[SP1-生命周期] ⑤ 销毁：@PreDestroy（容器正在关闭）");
    }
}
