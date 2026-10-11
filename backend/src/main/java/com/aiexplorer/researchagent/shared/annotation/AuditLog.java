package com.aiexplorer.researchagent.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解（场景⑤ 主角之一）——J7"自定义注解三件套"的业务落地版。
 *
 * <p>贴法：{@code @AuditLog("PAUSE_TASK")}——贴在"需要留痕"的 service 方法上。</p>
 *
 * <p>★ 它本身【零功能】（J7 铁律：注解只是便利贴，是"申请书"）——
 * 真正干活的是 {@code AuditAspect}（"读标签的外包工"）：
 * 读到这个注解 → 在方法前后插入"写审计记录"的逻辑。</p>
 *
 * <p>元注解解读（J7 三件套复用）：
 * <ul>
 *   <li>RUNTIME——必须活到运行时（切面靠反射读它——J7 的"改档实验"正是反例教训）；</li>
 *   <li>METHOD——只许贴方法（贴错地方编译器当场报错）。</li>
 * </ul></p>
 *
 * <p>参数用单参 {@code value}——享受 J7 讲的"value 特权"（可省名字写 {@code @AuditLog("PAUSE_TASK")}）。
 * 约定取值格式：大写动词_名词（CREATE_TASK / PAUSE_TASK ...）。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    /** 操作名（写进 audit_log.action 列）——如 "CREATE_TASK"、"PAUSE_TASK"。 */
    String value();
}
