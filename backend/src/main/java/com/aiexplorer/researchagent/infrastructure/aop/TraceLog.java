package com.aiexplorer.researchagent.infrastructure.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SP2 教学演示：方法追踪注解（与场景5 的 @AuditLog 同族，但不写库——只打日志，方便观察切面是否拦到）。
 * 标了本注解的方法会被 TraceLogAspect 的环绕通知包住。
 * （SP 线教学演示代码，可随时删除。）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceLog {

    /** 操作说明（切面日志里展示）。 */
    String value() default "";
}