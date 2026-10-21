package com.aiexplorer.researchagent.infrastructure.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SP8 教学演示：方法级角色要求（可删）。
 *
 * 与 Filter 的对比点：这个注解标在【方法】上，只有 Interceptor 能通过
 * HandlerMethod 读到它——Filter 拿不到"要执行哪个方法"。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色。 */
    String value();
}