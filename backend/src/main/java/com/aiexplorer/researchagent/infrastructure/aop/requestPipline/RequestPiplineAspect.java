package com.aiexplorer.researchagent.infrastructure.aop.requestPipline;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 请求管线练习 · 第 4 环：AOP 切面（通用骨架 @Around + proceed）。
 *
 * <p>骨架只有两件事：proceed() 之前放"前置"，之后放"后置"。
 * 本练习的前置/后置只是打日志——这就是"填进骨架里的内容"。</p>
 */
@Aspect
@Component
public class RequestPiplineAspect {

    @Around("@annotation(requestPiplineLog)")
    public Object around(ProceedingJoinPoint joinPoint, RequestPiplineLog requestPiplineLog) throws Throwable {
        long start = System.currentTimeMillis();
        System.out.println("[AOP]      >>> 进入切面（proceed 之前）");

        try {
            Object result = joinPoint.proceed();   // 放行：进到 Controller 方法
            System.out.println("[AOP]      <<< 退出切面（proceed 之后，耗时 "
                    + (System.currentTimeMillis() - start) + "ms）");
            return result;
        } catch (Throwable e) {
            System.out.println("[AOP]      <<< 异常退出（" + e.getClass().getSimpleName() + "），继续上抛");
            throw e;
        }
    }
}