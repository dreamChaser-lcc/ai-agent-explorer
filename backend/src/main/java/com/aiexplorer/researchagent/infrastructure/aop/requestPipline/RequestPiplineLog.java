package com.aiexplorer.researchagent.infrastructure.aop.requestPipline;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求管线练习 · 第 4 环的标记注解：贴了它的方法会被 RequestPiplineAspect 环绕。
 *
 * <p>为什么自定义注解：@Around("@annotation(xxx)") 的切点需要"锚点"——
 * 只拦截贴了这个注解的方法，而不是全部方法。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestPiplineLog {

    /** 说明文字（可选，练习里用来标注"这一环在干什么"）。 */
    String value() default "";
}