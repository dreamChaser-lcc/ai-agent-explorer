package com.aiexplorer.researchagent.infrastructure.aop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * SP2 实验载体：AOP 自调用失效（教学演示，可随时删除）。
 *
 * runDemo() 对比两种调用方式：
 *   1. this.loggedWork(...)   —— this 是真身，切面【不生效】（本课核心坑）
 *   2. self.loggedWork(...)   —— self 是代理（@Lazy 自注入），切面【生效】
 *
 * @Lazy 注入自己：Spring 给的是代理而不是真身——用它调用才能"经过代理"。
 */
@Service
public class AopDemoService {

    private final AopDemoService self;

    public AopDemoService(@Lazy AopDemoService self) {
        this.self = self;
    }

    public void runDemo() {
        System.out.println("[AOP-实验] ===== 1 this 自调用（预期：TraceLog 前后刀日志【不出现】） =====");
        this.loggedWork("1 this 自调用");

        System.out.println("[AOP-实验] ===== 2 经代理调用（预期：TraceLog 前后刀日志【出现】） =====");
        self.loggedWork("2 代理调用");

        System.out.println("[AOP-实验] ===== 结束（对比两种方式的日志差异） =====");
    }

    @TraceLog("AOP-实验：被追踪的方法")
    public void loggedWork(String how) {
        System.out.println("[AOP-实验] 业务方法执行：" + how);
    }
}