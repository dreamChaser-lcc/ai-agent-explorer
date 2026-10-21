package com.aiexplorer.researchagent.infrastructure.aop;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SP2 教学演示：@TraceLog 的切面（仿 AuditAspect 的写法，但不写库）。
 * 只打日志——让"切面是否拦到"在日志里一眼可见。
 * （SP 线教学演示代码，可随时删除。）
 */
@Aspect
@Component
public class TraceLogAspect {

    private static final Logger log = LoggerFactory.getLogger(TraceLogAspect.class);

    @Around("@annotation(traceLog)")
    public Object aroundTraced(ProceedingJoinPoint joinPoint, TraceLog traceLog) throws Throwable {
        log.info("[TraceLog] 前刀：拦到方法 = {}，实参 = {}",
                joinPoint.getSignature().toShortString(), Arrays.toString(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            log.info("[TraceLog] 后刀：正常返回");
            return result;
        } catch (Throwable exception) {
            log.info("[TraceLog] 后刀：异常退出（{}）——原样重抛", exception.getMessage());
            throw exception;
        }
    }
}