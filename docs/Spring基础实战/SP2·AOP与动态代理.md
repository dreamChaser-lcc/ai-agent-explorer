# SP2 · AOP 与动态代理

> **课程**：Spring 基础实战 · 模块一（2026-10-21）
> **已会**：场景⑤ 审计切面——标个注解，方法前后自动记审计日志，业务代码零改动
> **本次**：谁在干活——动态代理（JDK vs CGLIB）、切点、五种通知、**AOP 自调用失效**
> **对标**：廖雪峰 22.2「使用 AOP」
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **场景⑤ 审计切面**（[⑤AOP操作审计](../业务场景实战/⑤AOP操作审计.md)）：`@AuditLog("操作名")` + 切面自动写审计日志；
- **J2 回响**：调试时见过 CGLIB 代理类（类名带特征后缀）——**那就是代理**；
- **J7**：注解是申请书，框架靠**反射**读它。

---

## 一、AOP 是什么：三个前端类比一次讲透

| 前端概念 | 与 Spring AOP 的对应 |
|---|---|
| **`new Proxy(target, handler)`** | **动态代理**——JS Proxy 拦截 get/set/apply，代理拦截方法调用。**本质** |
| **axios 拦截器** | 通知（Advice）——请求前后插逻辑，不改业务代码 |
| **TypeScript 装饰器** `@log` | 注解 + 切面组合拳 |

**一句话**：AOP = Spring 启动时把"贴了切点注解的类"**换成代理对象**放进容器；调用先过代理增强，再进真身。

**场景⑤ 的秘密**：不是改了代码，是**换了演员**——注入的 Service 其实是 CGLIB 代理。

---

## 二、动态代理的两种实现：JDK vs CGLIB

| | JDK 动态代理 | CGLIB |
|---|---|---|
| 原理 | 运行时生成**接口的实现类**（Proxy.newProxyInstance + InvocationHandler） | 运行时生成**目标类的子类**（字节码，方法覆写 + MethodInterceptor） |
| 要求 | 目标类**必须实现接口** | 类和方法**不能是 final**（子类要覆写） |
| Spring Boot 默认 | —— | ✅ **proxyTargetClass=true（默认 CGLIB）** |

**证据在调试器里**（J2 课程）：断点看到的代理类名 = 目标类名 + CGLIB 特征后缀（两个美元符号 + 数字）——看到这种名字 = 你拿的是代理，不是真身。

**为什么默认 CGLIB**：不要求写接口。本项目 `ResearchTaskCommandService` 没实现接口，用的就是 CGLIB。

---

## 三、读懂项目里的真实切面（AuditAspect 逐段拆）

位置：`infrastructure/aop/AuditAspect.java`（场景⑤）

```java
@Aspect                          // ① 声明"这是切面"（外包工上岗证）
@Component                       //    且它自己也是个 bean
@Order(1)                        // ② 数字越小越靠外——审计包在事务代理【外层】，
                                 //    记到的才是"最终结局"（提交后成功/回滚后失败）
public class AuditAspect {

    @Around("@annotation(auditLog)")          // ③ 环绕通知 + 切点：只拦标了 @AuditLog 的方法
    public Object aroundAudited(
            ProceedingJoinPoint joinPoint,    // ④ 调用现场：能拿实参、能放行
            AuditLog auditLog)                //    反射拿到注解实例
            throws Throwable {
        String action = auditLog.value();                 // 干了什么
        String targetId = firstUuidArg(joinPoint.getArgs()); // 对谁
        try {
            Object result = joinPoint.proceed();          // ★ 放行：进真身执行
            auditLogService.recordSuccess(action, targetId); // 后刀·成功
            return result;
        } catch (Throwable exception) {
            auditLogService.recordFailure(action, targetId, exception); // 后刀·失败
            throw exception;   // ★★ 必须原样重抛——审计绝不能吃掉业务异常
        }
    }
}
```

**五个要点**：

1. **@Around 是最强通知**：前后逻辑都在手里，proceed() 前是"前刀"、之后是"后刀"；
2. **切点 @annotation(auditLog)**：按注解过滤（还有 execution 按方法签名过滤）；
3. **异常必须重抛**：吞掉异常 = 状态机/全局异常处理全废；
4. **@Order 控制多层代理嵌套顺序**：审计在外、事务在内；
5. **通知家族五种**：@Before / @After / @AfterReturning / @AfterThrowing / @Around。

---

## 四、★ 本课核心：AOP 失效场景（自调用最经典）

### 4.1 心智模型

```text
你（调用方） ──调用──→ 【代理对象】 ──增强逻辑──→ 【真身对象】.method()
```

**增强只发生在代理上**。绕过代理直接调真身 = 没有增强。

### 4.2 自调用失效（最经典的坑）

```java
@Service
public class OrderService {
    public void createOrder() {
        this.validate();     // ← this 是【真身】，不是代理！切面不生效！
    }

    @Transactional
    public void validate() { ... }
}
```

**为什么**：`this.validate()` 编译后就是普通方法跳转，**根本不经过代理对象**。

前端类比：你拿着组件实例直接调 `instance.method()`，绕过了被 Proxy 包装的引用——拦截逻辑当然不触发。

### 4.3 失效场景全家福

| 失效场景 | 原因 | 典型症状 |
|---|---|---|
| **自调用**（this.method()） | 没经过代理 | 事务不回滚 / 审计不记录 / 缓存不生效 |
| 非 public 方法 | CGLIB 子类无法正确覆写语义 | 增强静默丢失 |
| final 方法 | CGLIB 无法覆写 | 增强静默丢失 |
| 异常被吞 | 通知的"失败分支"感知不到 | 审计全记成功 / 事务不回滚 |

### 4.4 修复方式（按推荐排序）

1. **重新设计**：把被自调用的方法拆到另一个 bean（最干净——循环依赖/自调用往往意味着职责纠缠）；
2. **注入自身代理**：`@Lazy` 注入自己（`AopDemoService self`），用 `self.method()` 走代理；
3. `AopContext.currentProxy()`（需开启 exposeProxy，较繁琐）。

---

## 五、实验记录（2026-10-21 端到端实测）

实验载体：`infrastructure/aop/` 下的 `TraceLog`（注解）+ `TraceLogAspect`（切面）+ `AopDemoService`（自注入代理）+ `AopDemoRunner`（启动后自动跑）。教学演示代码，可随时删除。

```text
[AOP-实验] ===== 1 this 自调用（预期：TraceLog 前后刀日志【不出现】） =====
[AOP-实验] 业务方法执行：1 this 自调用
        ← 【没有任何 TraceLog 日志】——切面失效，实锤！

[AOP-实验] ===== 2 经代理调用（预期：TraceLog 前后刀日志【出现】） =====
[TraceLog] 前刀：拦到方法 = AopDemoService.loggedWork(..)，实参 = [2 代理调用]
[AOP-实验] 业务方法执行：2 代理调用
[TraceLog] 后刀：正常返回
        ← 【切面生效】，实锤！
```

**结论**：同一个方法、同一行调用代码，**唯一区别是"经不经过代理"**——AOP 生效与否的判据就这一条。

**修复方式实测**：`@Lazy` 注入自身（`AopDemoService(@Lazy AopDemoService self)`）——Spring 给的是**代理**，`self.loggedWork()` 走代理 → 切面生效。

### 5.1 本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| [`TraceLog.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/aop/TraceLog.java) | 教学注解（仿 @AuditLog，但不写库，只打日志） | ✅ 可删 |
| [`TraceLogAspect.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/aop/TraceLogAspect.java) | @Around 切面（前刀/后刀日志，证明切面是否拦到） | ✅ 可删 |
| [`AopDemoService.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/aop/AopDemoService.java) | 自调用 vs 代理调用对比（`@Lazy` 自注入代理） | ✅ 可删 |
| [`AopDemoRunner.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/aop/AopDemoRunner.java) | 启动后自动执行一次实验（ApplicationRunner） | ✅ 可删 |

> 删除时必须**成组删**：`AopDemoRunner` 引用 `AopDemoService`，`AopDemoService` 引用 `TraceLog`，`TraceLogAspect` 依赖 `TraceLog`——单独删一个会编译失败。

---

## 六、项目关联地图

| 概念 | 项目位置 |
|---|---|
| 真实切面教材 | `AuditAspect`（场景⑤：@Around + @annotation 切点 + @Order(1) 在事务外层） |
| 注解族 | `@AuditLog`（场景⑤）/ `@TraceLog`（SP2 演示） |
| 自注入代理 | `AopDemoService(@Lazy AopDemoService self)`（SP2 演示） |
| 事务即代理 | 场景②④ 的 `@Transactional`（SP4 深入传播行为） |
| 缓存即代理 | 场景⑦ 的 `@Cacheable`（AOP 家族成员） |

---

## 七、思考题（自查 + 参考答案）

1. 场景⑤ 的 `AuditAspect` 用 `@Order(1)` 包在事务**外层**——如果放在事务内层，审计日志会记录到什么"结局"？哪个更符合审计需求？<br>
   **参考答案**：放**内层**时，审计在事务提交**之前**执行——如果事务随后回滚，审计已经记了"成功"，而数据库实际回滚了 → **假成功记录**。放**外层**（`@Order` 数字更小）记录的是事务的最终结局（提交成功 / 回滚失败），符合审计"如实记录最终结果"的需求。
2. `@Around` 里忘写 `joinPoint.proceed()` 会发生什么？忘写 `return result` 会发生什么？<br>
   **参考答案**：忘写 `proceed()` → **目标方法根本不执行**，业务逻辑被整个吞掉（调用方拿到 null）；忘写 `return result` → 目标方法执行了但**返回值丢失**（调用方收到 null，可能 NPE）。两者都是"静默错误"——日志看着正常、结果不对，最难排查。
3. 同一个类里，`methodA()` 调 `methodB()`，methodB 标了 `@Cacheable`——缓存生效吗？三种修复方式分别是什么？<br>
   **参考答案**：**不生效**（`this.methodB()` 绕过代理，与本次实验验证的自调用失效同源）。三种修复：① 把 methodB 拆到另一个 bean（最干净）；② `@Lazy` 注入自身代理，用 `self.methodB()`；③ `AopContext.currentProxy().methodB()`（需开启 `exposeProxy=true`，较繁琐）。
4. 为什么 Spring Boot 2.x 之后默认改用 CGLIB？（提示：大家都不爱写接口了）
   **参考答案**：JDK 动态代理**要求目标类实现接口**；现代项目流行"直接注入实现类、不写接口"。CGLIB 用**子类继承**的方式代理，不要求接口——Spring Boot 2.x 起 `spring.aop.proxy-target-class=true` 成为默认。代价：final 类和 final 方法无法代理。

---

## 八、本课问答记录（2026-10-21）

（待补——学员提问后按双台账纪律记录）
