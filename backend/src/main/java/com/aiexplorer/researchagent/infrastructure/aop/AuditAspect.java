package com.aiexplorer.researchagent.infrastructure.aop;

import com.aiexplorer.researchagent.application.service.AuditLogService;
import com.aiexplorer.researchagent.shared.annotation.AuditLog;
import java.util.Arrays;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 审计切面（场景⑤ 主角之二）——J7"申请书 → 外包工 → 戏服"的实物化。
 *
 * <p>工作机制（谁声明、谁做、谁转发）：
 * <ul>
 *   <li>{@code @Aspect} —— 声明"这是外包工"；Spring 启动时发现它，
 *       并给【贴了 @AuditLog 的类】造代理（CGLIB 戏服——J2 的 $$SpringCGLIB$$0 回响）；</li>
 *   <li>{@code @Around} —— 刀法"环绕"：方法执行的前后都能插入逻辑，中间用 proceed() 放行；</li>
 *   <li>{@code @annotation(auditLog)} —— 切点：只拦"贴了这个注解的方法"（反射读标签——J7）。</li>
 * </ul></p>
 *
 * <p>★ 业务代码零改动——"零改动的秘密不在改代码，在换演员"（你调的是代理替身）。</p>
 */
@Aspect
@Component
@Order(1)
// ↑ 数字越小越靠【外】。审计要包在 @Transactional 事务代理的外层——
//    这样记录到的才是"最终结局"：事务提交后的成功 / 回滚后的失败。
public class AuditAspect {

    private final AuditLogService auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 环绕通知：贴了 @AuditLog 的方法都会被这里"包住"。
     *
     * @param joinPoint 连接点（被打劫的方法调用现场——能拿到实参、能 proceed 放行）
     * @param auditLog  注解实例（J7：反射读标签得到它——action = auditLog.value()）
     */
    @Around("@annotation(auditLog)")
    public Object aroundAudited(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String action = auditLog.value();                     // 干了什么——来自注解（反射读标签）
        String targetId = firstUuidArg(joinPoint.getArgs());  // 操作对象——从实参里抓第一个 UUID

        try {
            Object result = joinPoint.proceed();              // ★ 放行：真正执行目标方法（进入"真身"）
            auditLogService.recordSuccess(action, targetId);  // 后刀·成功
            return result;                                    // 原样把返回值送回调用方
        } catch (Throwable exception) {
            auditLogService.recordFailure(action, targetId, exception); // 后刀·失败（失败也要留痕）
            throw exception;   // ★★ 必须原样重抛——审计绝不能"吃掉"业务异常：
                               //    否则状态机拒绝（409/400）、值班室兜底、调用方逻辑会全部失灵
        }
    }

    /** 从方法实参里找第一个 UUID（如 taskId）作为操作对象 id——找不到返回 null（如 createTask）。 */
    private String firstUuidArg(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg instanceof UUID)
                .map(String::valueOf)
                .findFirst()
                .orElse(null);
    }
}
