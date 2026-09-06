# Spring AOP 详解与实战手册

> 适用版本：Spring Boot 3.5（本项目）/ Java 21
> 最后更新：2026-09-05
>
> 配套文档：
> - `docs/Spring Bean与事务管理详解.md`（事务就是 AOP 最典型的应用，建议连着看）
> - `docs/Spring生态三层对比与详解.md`
> - `docs/Java后端问答知识汇总.md`

---

## 目录

- [一、AOP 是什么](#一aop-是什么)
- [二、七个核心概念](#二七个核心概念)
- [三、五种通知与执行顺序](#三五种通知与执行顺序)
- [四、底层原理：动态代理](#四底层原理动态代理)
- [五、切点表达式 execution 语法](#五切点表达式-execution-语法)
- [六、Spring 自己用 AOP 做了什么](#六spring-自己用-aop-做了什么)
- [七、Spring AOP vs AspectJ](#七spring-aop-vs-aspectj)
- [八、Filter / Interceptor / AOP 三层拦截对比](#八filter--interceptor--aop-三层拦截对比)
- [九、动手实战：三个可直接用的切面](#九动手实战三个可直接用的切面)
- [十、常见坑与排查方法](#十常见坑与排查方法)
- [十一、本项目接入 AOP 的步骤](#十一本项目接入-aop-的步骤)
- [十二、速记口诀](#十二速记口诀)

---

## 一、AOP 是什么

**AOP（Aspect-Oriented Programming，面向切面编程）**：把散落在各个业务方法中的**横切关注点**抽出来，定义一次，再"织入"到目标方法的指定位置。

### 横切关注点是什么

与业务无关，但"到处都要做"的事：

| 横切关注点 | 举例 |
|---|---|
| 事务 | BEGIN / COMMIT / ROLLBACK |
| 日志 | 方法入参、出参、耗时 |
| 权限 | 判断当前用户能否执行 |
| 异常处理 | 统一捕获、上报 |
| 缓存 | 先查缓存，未命中再查库 |
| 性能监控 | 打点上报 TP99 |
| 幂等/限流 | 重复提交拦截 |

### 没有 AOP vs 有 AOP

```java
// 没有 AOP：每个方法都要手写一遍，业务代码被淹没
public void createTask(...) {
    TransactionStatus status = transactionManager.begin();
    try {
        // ... 真正的业务逻辑只有 5 行
        transactionManager.commit(status);
    } catch (Exception e) {
        transactionManager.rollback(status);   // 每个方法都复制这 10 行
        throw e;
    }
}

// 有 AOP：一个注解搞定，业务代码干净
@Transactional
public void createTask(...) {
    // ... 只有业务逻辑
}
```

### 本项目现状（重要）

本项目 **没有自己写切面**（`src/main/java` 下没有 `@Aspect` 类），但**一直在用 AOP** —— 事务就是 Spring 内置的 AOP 切面：

| 位置 | 用的 AOP 能力 |
|---|---|
| `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskCommandService.java:53` | `@Transactional`（创建任务事务） |
| `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskControlService.java:51` | `@Transactional`（确认计划事务） |
| `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskQueryService.java:42` | `@Transactional(readOnly = true)`（只读事务） |

> 注意：`backend/pom.xml` 目前**未引入** `spring-boot-starter-aop`。事务能用是因为 `spring-boot-starter-data-jpa` 传递依赖了 `spring-aop` + `aspectjweaver`；
> 但要自己写 `@Aspect` 切面，建议显式引入 starter（见 [第十一章](#十一本项目接入-aop-的步骤)）。

---

## 二、七个核心概念

| 概念 | 英文 | 含义 | 类比 |
|---|---|---|---|
| 切面 | Aspect | 横切逻辑的模块化载体，一个 `@Aspect` 类 | 一整套"安保方案" |
| 通知 | Advice | 切面里具体执行的动作 + **执行时机** | 方案里的"进门查证 / 出门登记" |
| 连接点 | Join Point | 理论上能被织入的位置 | 大楼里所有的门 |
| 切点 | Pointcut | 从连接点中筛选出的、真正要织入的那些 | "只管 3 楼以上" |
| 目标对象 | Target | 被代理的原始 Bean | 被保护的公司 |
| 代理对象 | Proxy | 织入后对外暴露的对象 | 大门口的保安亭 |
| 织入 | Weaving | 把切面代码加到目标上、生成代理的过程 | 安装保安亭 |

**关键认知**：Spring AOP 中**连接点只有"方法执行"一种**（AspectJ 还能拦构造器、字段读写）。所以切点表达式匹配的一定是方法。

**一句话串起来**：
> 切面（Aspect）= 切点（Pointcut，在哪织）+ 通知（Advice，织什么、什么时候织）；织入（Weaving）后得到代理（Proxy），调用先过代理再进目标（Target）。

---

## 三、五种通知与执行顺序

### 3.1 五种通知

| 通知 | 时机 | 能阻止方法执行 | 能修改返回值 | 典型用途 |
|---|---|---|---|---|
| `@Around` | 包裹整个方法 | ✅ 不调 `proceed()` 就拦下 | ✅ 可替换返回值 | 耗时统计、限流、缓存、事务 |
| `@Before` | 方法执行前 | ✅ 抛异常即可 | ❌ | 权限校验、参数预处理、日志 traceId |
| `@AfterReturning` | 正常返回后 | ❌ | ❌（只能读） | 记录成功日志、审计 |
| `@AfterThrowing` | 抛异常后 | ❌ | ❌ | 异常告警、补偿 |
| `@After` | 无论成败都执行（finally 语义） | ❌ | ❌ | 释放资源、清理 ThreadLocal |

### 3.2 `@Around` 的写法（唯一能控制"是否执行"的通知）

```java
@Around("pointcut()")
public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    // ① 前置逻辑（对应 @Before 的位置）
    long startTime = System.currentTimeMillis();
    try {
        // ② 执行目标方法 —— 不调用它，目标方法就不会执行！
        return joinPoint.proceed();
    } finally {
        // ③ 后置逻辑（finally 保证成功失败都执行）
        log.info("耗时 {}ms", System.currentTimeMillis() - startTime);
    }
}
```

`ProceedingJoinPoint` 常用 API：

| 方法 | 作用 |
|---|---|
| `proceed()` | 执行目标方法（可反复调用 = 重试） |
| `proceed(Object[] args)` | **用新参数**执行目标（改参只能在这做） |
| `getArgs()` | 拿到方法入参数组 |
| `getSignature()` | 方法签名（`toShortString()` 得到 `UserService.save(..)`） |
| `getTarget()` | 原始目标对象 |
| `getThis()` | 代理对象 |

### 3.3 执行顺序（常考）

**正常流程：**
```
@Around 前半段
  → @Before
    → 目标方法执行
  → @AfterReturning
  → @After（finally）
@Around 后半段
```

**异常流程：**
```
@Around 前半段
  → @Before
    → 目标方法抛异常
  → @AfterThrowing
  → @After（finally）
  → 异常继续向外抛（@Around 后半段不执行，除非你自己 try-catch 吃掉）
```

**多个切面同时命中时的顺序**：默认按切面类的 Bean 名字排序，不可靠。**必须显式指定**用 `@Order(数字)` 或实现 `Ordered` 接口——**数字越小越靠外（越先执行、越后结束）**。

```java
@Aspect
@Order(1)   // 数字小 = 在外层；事务切面通常要在最外层
public class TransactionAspect { }
```

---

## 四、底层原理：动态代理

Spring AOP 是**运行时代理**，不修改字节码。

### 4.1 两种代理方式

| 条件 | 使用 | 原理 |
|---|---|---|
| 目标类**实现了接口** | **JDK 动态代理** | 运行时生成一个实现同样接口的代理类，`InvocationHandler` 拦截 |
| 目标类**没实现接口** | **CGLIB** | 生成目标类的**子类**，重写非 final 方法 |
| `spring.aop.proxy-target-class=true` | 强制全部 CGLIB | **Spring Boot 2.x 起这就是默认值** |

> 所以 Boot 2.0 之后，即便你的类实现了接口，默认也是 CGLIB。别再背"有接口就用 JDK"的老结论了。

### 4.2 调用链

```
调用方
  ↓ 拿到的是代理对象（容器里存的是它）
代理对象
  ↓ 走拦截器链（MethodInterceptor 链）
  ↓ 依次执行 @Around → @Before → ...
目标对象.method()   ← 真正的业务代码
  ↓ 返回
代理对象（执行 @AfterReturning → @After → @Around 后半段）
  ↓
调用方拿到返回值
```

事务的 BEGIN/COMMIT 就写在拦截器链里的 `TransactionInterceptor` 中，你的代码里根本没有事务语句。

### 4.3 代理机制带来的三个限制（必考）

#### ① 同类内部自调用会绕过代理 → 注解失效

```java
@Service
public class TaskService {
    @Transactional
    public void methodA() {
        this.methodB();     // ❌ 真实对象内部自调用，没经过代理
    }

    @Transactional          // ❌ 这个事务不会生效
    public void methodB() { }
}
```

口诀：**代理只拦"从外面打进来的电话"，不拦"屋子里面自己喊"**。
同样失效的还有 `@Async`、`@Cacheable`、自定义 `@Aspect`。

**三种解法：**

| 解法 | 写法 | 评价 |
|---|---|---|
| 拆到另一个 Bean（推荐） | `otherService.methodB()` | 最干净，符合单一职责 |
| 注入自身代理 | `@Autowired TaskService self; self.methodB();` | 简单，但有点别扭 |
| `AopContext` | `((TaskService) AopContext.currentProxy()).methodB()` | 需开 `exposeProxy=true`，不推荐 |

#### ② 方法必须是 `public`、`非 final`、`非 static`

CGLIB 靠**重写**方法增强：`private` 无法重写、`final` 禁止重写、`static` 不属于对象，统统增强不了（注解不报错，只是静默失效——最难查的那类 bug）。

#### ③ 自己 `new` 出来的对象完全没有代理

Spring 只代理**容器里的 Bean**。`new TaskService()` 的对象上的一切注解全部无效。

---

## 五、切点表达式 execution 语法

### 5.1 结构

```
execution( [修饰符] 返回类型 [包名.类名.]方法名(参数列表) [throws 异常] )
```

```java
@Pointcut("execution(* com.aiexplorer.researchagent.application..*Service.*(..))")
public void serviceLayer() { }
```

拆解：

| 片段 | 含义 |
|---|---|
| `execution(` | 匹配方法执行（Spring 只支持这一种） |
| `*`（第一个） | 返回类型任意 |
| `...application..` | `application` 包**及其所有子包**（`..` 是关键） |
| `*Service` | 类名以 `Service` 结尾 |
| `.*(..)` | 任意方法名 + 任意参数（`..` = 参数个数类型不限） |

### 5.2 常用表达式速查

| 表达式 | 匹配范围 |
|---|---|
| `execution(* *(..))` | 所有方法（慎用，性能差） |
| `execution(public * *(..))` | 所有 public 方法 |
| `execution(* set*(..))` | 所有 `set` 开头的方法 |
| `execution(* com.x.service.*.*(..))` | service 包下所有类（**不含子包**） |
| `execution(* com.x.service..*.*(..))` | service 包**及子包**（多一个 `..`） |
| `execution(* *..*Service.save*(..))` | 任意包下 `Service` 结尾类的 `save` 开头方法 |
| `within(com.x.service..*)` | 类级匹配（比 execution 快，按类筛选） |
| `@annotation(org.springframework.transaction.annotation.Transactional)` | **带某注解的方法** ← 事务切面就是这么写的 |
| `@within(org.springframework.stereotype.Service)` | 类上有某注解的所有方法 |
| `bean(*Service)` | 按 Bean 名字匹配（Spring 特有，好用） |
| `args(java.util.UUID, ..)` | 第一个参数是 UUID 的方法 |

### 5.3 组合与复用

```java
@Pointcut("execution(* com.aiexplorer..*Service.*(..))")
public void serviceLayer() { }

@Pointcut("execution(* com.aiexplorer..*Controller.*(..))")
public void controllerLayer() { }

// 与：&&   或：||   非：!
@Pointcut("serviceLayer() || controllerLayer()")
public void allLayers() { }
```

---

## 六、Spring 自己用 AOP 做了什么

理解这一节的最好方式：**你已经天天在用 AOP，只是没意识到。**

| 注解 | 底层切面/拦截器 | 作用 |
|---|---|---|
| `@Transactional` | `TransactionInterceptor` | 声明式事务 |
| `@Cacheable` / `@CacheEvict` | `CacheInterceptor` | 声明式缓存 |
| `@Async` | `AsyncExecutionInterceptor` | 方法异步执行 |
| `@Valid` + 方法校验 | `MethodValidationInterceptor` | 方法级参数校验 |
| `@Retryable`（Spring Retry） | `RetryInterceptor` | 失败重试 |
| `@Scheduled` | `ScheduledAnnotationBeanPostProcessor` | 定时任务 |
| `@PreAuthorize`（Security） | `MethodSecurityInterceptor` | 方法级鉴权 |

**它们的实现套路完全一致**：注解 → 切点（`@annotation(...)` 或 `@within(...)`）→ 拦截器（实现 `MethodInterceptor`）→ 在 `invoke()` 里写横切逻辑 → 生成代理。

你写自定义切面时，照抄这个套路就行。

---

## 七、Spring AOP vs AspectJ

| 维度 | Spring AOP | AspectJ |
|---|---|---|
| 织入时机 | **运行期**（生成代理对象） | **编译期**（ajc）或类加载期（LTW） |
| 实现方式 | 代理模式 | 直接改 `.class` 字节码 |
| 连接点范围 | **仅方法执行** | 方法、构造器、字段读写、静态块、异常处理 |
| 性能 | 有代理调用开销（一层反射/调用） | 无额外开销，更快 |
| 侵入性 | 零侵入，纯 Java 配置 | 需要 ajc 编译器或 `-javaagent` |
| 同类自调用 | ❌ 失效 | ✅ 生效（字节码已改） |
| private/final 方法 | ❌ 增强不了 | ✅ 可以 |
| 学习成本 | 低 | 高 |
| 适用场景 | **99% 的业务需求** | 需要拦截构造器/字段，或极致性能 |

**结论**：Spring 生态里用 Spring AOP 就够了。真正需要 AspectJ 的场景极少（典型如深度性能监控、领域事件自动埋点、需要增强非 public 方法的框架开发）。

---

## 八、Filter / Interceptor / AOP 三层拦截对比

```
Filter（Servlet 规范，Tomcat 层）
   ↓
Interceptor（Spring MVC 层，拦 Controller）
   ↓
AOP（Spring 容器层，拦任意 Bean 方法）
```

| | Filter | Interceptor | AOP |
|---|---|---|---|
| 所属层 | Servlet 容器 | Spring MVC | Spring 容器（Bean 代理） |
| 实现方式 | 函数回调 | `HandlerInterceptor` | 动态代理 |
| 能拿到 | 原始 `request`/`response` | handler 对象、request/response | **方法参数与返回值**（最强） |
| 拿不到 | Controller / Bean 信息 | 方法参数值 | HTTP 对象（除非自己传） |
| 依赖 | 不依赖 Spring | 依赖 Spring MVC | 依赖 Spring 容器 |
| 典型用途 | 编码、CORS、粗粒度鉴权、XSS 过滤 | 登录校验、日志 traceId、接口耗时 | 事务、缓存、权限注解、审计 |
| 本项目例子 | 未自定义（CORS 用 `WebCorsConfiguration` 配置） | 未使用（可直接用 AOP 替代） | `@Transactional` 全项目 |

**选型口诀**：
> 要动 HTTP 报文 → Filter；要拦所有 Controller 且需要 handler 信息 → Interceptor；要在**业务方法**前后做事（事务、缓存、审计）→ AOP。

---

## 九、动手实战：三个可直接用的切面

### 9.1 接口耗时统计（最常用）

```java
@Aspect
@Component
@Order(100)          // 数字大 = 内层，不干扰事务切面
public class ServiceCostTimeAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceCostTimeAspect.class);

    /** 切点：application 包及子包下，所有 Service 的所有方法 */
    @Pointcut("execution(* com.aiexplorer.researchagent.application..*Service.*(..))")
    public void serviceMethods() { }

    @Around("serviceMethods()")
    public Object recordCostTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        try {
            return joinPoint.proceed();
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            if (costTime > 1000) {                      // 只记慢调用，避免日志爆炸
                LOGGER.warn("[慢调用] {} 耗时 {}ms", methodName, costTime);
            } else {
                LOGGER.debug("{} 耗时 {}ms", methodName, costTime);
            }
        }
    }
}
```

### 9.2 操作审计（记录"谁在什么时候改了什么"）

```java
@Aspect
@Component
public class TaskAuditAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskAuditAspect.class);

    /** 切点：带 @Auditable 自定义注解的方法 */
    @Pointcut("@annotation(com.aiexplorer.researchagent.shared.annotation.Auditable)")
    public void auditableMethods() { }

    @AfterReturning(pointcut = "auditableMethods()", returning = "result")
    public void writeAuditLog(JoinPoint joinPoint, Object result) {
        LOGGER.info("[审计] 操作={} 参数={} 结果={}",
                joinPoint.getSignature().toShortString(),
                Arrays.toString(joinPoint.getArgs()),
                result);
    }
}
```

配套注解：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)   // 必须 RUNTIME，否则运行时读不到
public @interface Auditable { }
```

### 9.3 统一异常告警（把 Service 异常转成事件上报）

```java
@Aspect
@Component
public class ExceptionAlertAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionAlertAspect.class);

    @AfterThrowing(
            pointcut = "execution(* com.aiexplorer.researchagent.application..*Service.*(..))",
            throwing = "exception")
    public void alertOnException(JoinPoint joinPoint, Exception exception) {
        LOGGER.error("[异常告警] 方法={} 异常={} 消息={}",
                joinPoint.getSignature().toShortString(),
                exception.getClass().getSimpleName(),
                exception.getMessage());
    }
}
```

### 9.4 优先级提醒

如果同时写了"耗时统计"和"事务"切面，注意 `@Order`：**事务切面要在最外层**（数字小），否则耗时统计会把"事务提交"的时间也算进去，或者事务回滚时统计到错误的耗时。

---

## 十、常见坑与排查方法

| 坑 | 现象 | 原因与解决 |
|---|---|---|
| 注解放了但没生效 | 日志里没有切面输出 | ① 方法非 public；② 同类 `this.` 自调用；③ 类没注册成 Bean（漏 `@Component`）；④ 切点表达式写错 |
| 切点写错导致全项目被拦 | 接口全部变慢 | `execution(* *(..))` 太宽泛，用 `within()` 限定包路径 |
| 多个切面顺序混乱 | 耗时统计串了 | 加 `@Order` 显式指定 |
| `@Around` 忘记 `proceed()` | 方法逻辑"消失"，返回 null | 必须调用并**返回**其结果，不能只调不 return |
| `@Around` 吞掉异常 | 事务不回滚 | 异常必须继续往外抛，别在切面里 try-catch 掉 |
| 循环依赖报错 | 启动报 `BeanCurrentlyInCreationException` | 用构造器注入 + 拆分职责；别用字段注入掩盖问题 |
| CGLIB 报 final 类错误 | 启动失败 | 目标类被 `final` 修饰，无法生成子类 |
| 切面类自己被切面拦截 | 递归调用栈溢出 | 切点排除切面包：`!within(com.aiexplorer..aspect..*)` |

### 排查三板斧

```bash
# ① 看这个 Bean 到底是不是代理对象（类名带 $$ 或 $EnhancerBySpringCGLIB$$ 就是 CGLIB 代理）
curl http://localhost:8080/actuator/beans | grep yourServiceName

# ② 打开 AOP 调试日志，看切点匹配到了哪些方法
logging.level.org.springframework.aop=DEBUG

# ③ 打印代理类真实 class
System.out.println(taskService.getClass().getName());
// com.aiexplorer...TaskService$$SpringCGLIB$$0   ← 说明被代理了
```

---

## 十一、本项目接入 AOP 的步骤

当前 `backend/pom.xml` 未显式引入 aop starter，自建切面需先加依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

> Boot 中 `spring.aop.auto` 默认为 `true`（自动开启 `@EnableAspectJAutoProxy`），**不需要**手动加 `@EnableAspectJAutoProxy`。

建议落地顺序（由易到难）：

1. **耗时统计切面**：切 `application..*Service`，先只 `debug` 输出，验证切点命中
2. **审计切面**：在 `confirmPlan`、`cancelTask` 等状态变更方法上挂自定义注解（对应
   `backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskControlService.java:51`）
3. **异常告警切面**：`@AfterThrowing` 统一记录服务层异常

**不建议一开始就做的**：在事务方法上套 `@Around` 做重试（重试语义与事务边界耦合，极易出 double-commit 问题，应交给专门的 Retry 组件）。

---

## 十二、速记口诀

1. **AOP = 横切关注点抽出来，一次定义到处织入**；事务就是它最典型的应用
2. **切面 = 切点（在哪织）+ 通知（织什么、何时织）**
3. **五种通知**：`Around` 包全部（唯一能拦、能改返回值）、`Before` 前置、`AfterReturning` 成功后、`AfterThrowing` 异常后、`After` finally
4. **执行顺序**：Around 前 → Before → 方法 → AfterReturning → After → Around 后；多切面用 `@Order`，**数字小在外层**
5. **底层是代理**：Boot 2.x 起默认 CGLIB（生成子类），不是"有接口就用 JDK"
6. **三大失效**：非 public / final 方法、`this.` 同类自调用、自己 `new` 的对象
7. **切点表达式**：`execution(返回类型 包.类.方法(参数))`，`..` 表示"包及子包 / 任意参数"
8. **Spring AOP 只拦方法**，AspectJ 才拦构造器与字段；99% 场景 Spring AOP 足够
9. **三层拦截**：Filter 动报文、Interceptor 拦 Controller、AOP 管业务方法
10. **`@Around` 必须 `return joinPoint.proceed()`**，不 return 返回 null，不调则方法不执行

---

## 附：一图流总结

```
                    ┌─────────────────────────────────────┐
   调用方 ────────▶ │  代理对象 Proxy（CGLIB 子类）        │
                    │  ┌───────────────────────────────┐  │
                    │  │ @Order(1) 事务切面             │  │
                    │  │  ┌─────────────────────────┐  │  │
                    │  │  │ @Order(100) 日志切面     │  │  │
                    │  │  │   ┌─────────────────┐   │  │  │
                    │  │  │   │  目标方法        │   │  │  │
                    │  │  │   │  （业务代码）    │   │  │  │
                    │  │  │   └─────────────────┘   │  │  │
                    │  │  └─────────────────────────┘  │  │
                    │  └───────────────────────────────┘  │
                    └─────────────────────────────────────┘

    切面 = 切点（Pointcut：在哪织）+ 通知（Advice：织什么、何时织）
    织入（Weaving）= 生成上面这个代理对象的过程（运行期完成）
```
