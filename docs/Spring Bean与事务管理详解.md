# Spring Bean 与事务管理详解

> 适用版本：Spring Boot 3.5（本项目）/ Java 21 / Spring Data JPA + H2
> 最后更新：2026-09-05
>
> 配套文档：
> - `docs/Spring AOP详解与实战手册.md`（事务的底层就是 AOP，建议先读或对照读）
> - `docs/Java后端问答知识汇总.md`
> - `docs/后端架构与执行流程.md`

---

## 目录

- [第一部分：Bean](#第一部分bean)
  - [1.1 IoC / DI 与 Bean 的本质](#11-ioc--di-与-bean-的本质)
  - [1.2 注册 Bean 的两种方式](#12-注册-bean-的两种方式)
  - [1.3 注入 Bean 的三种方式](#13-注入-bean-的三种方式)
  - [1.4 同类型多个 Bean 怎么选](#14-同类型多个-bean-怎么选)
  - [1.5 Bean 的作用域](#15-bean-的作用域)
  - [1.6 Bean 的完整生命周期](#16-bean-的完整生命周期)
  - [1.7 条件装配](#17-条件装配)
  - [1.8 循环依赖](#18-循环依赖)
- [第二部分：事务](#第二部分事务)
  - [2.1 事务是什么（ACID）](#21-事务是什么acid)
  - [2.2 编程式 vs 声明式](#22-编程式-vs-声明式)
  - [2.3 @Transactional 底层原理](#23-transactional-底层原理)
  - [2.4 传播行为 propagation](#24-传播行为-propagation)
  - [2.5 隔离级别 isolation](#25-隔离级别-isolation)
  - [2.6 其他属性](#26-其他属性rollbackforreadonlytimeout)
  - [2.7 七大失效场景](#27-七大失效场景)
  - [2.8 编程式事务 TransactionTemplate](#28-编程式事务-transactiontemplate)
- [第三部分：结合本项目](#第三部分结合本项目)
- [第四部分：速查与口诀](#第四部分速查与口诀)

---

# 第一部分：Bean

## 1.1 IoC / DI 与 Bean 的本质

### IoC（控制反转）

传统写法：**你**负责 `new` 对象、管它的依赖、管它的销毁。

IoC：把**创建与组装的控制权反转给容器**——你不 `new`，容器帮你建、帮你装、帮你管一生。

```
传统：    A a = new A();  B b = new B(a);   ← 你自己拼装
IoC：     容器读取配置 → 创建 A 和 B → 把 A 塞进 B → 你要用直接声明
```

### DI（依赖注入）

IoC 的具体实现手段：**你只声明"我需要什么"，容器把依赖送进来**。

### Bean 的定义

> **Bean = 由 Spring 容器创建、组装并管理生命周期的 Java 对象。**

| | `new` 出来的对象 | Bean |
|---|---|---|
| 谁创建 | 你 | Spring 容器 |
| 依赖谁填 | 你手动传 | 容器自动注入 |
| 有几个实例 | 每次 `new` 都新的 | 默认**单例**（全应用一个） |
| 有没有额外能力 | 无 | 事务、缓存、AOP 增强… |
| 能否被 AOP 代理 | ❌ | ✅ |

**这是理解事务的关键**：`@Transactional` 只对 Bean 生效，因为它靠代理实现（见 [2.3](#23-transactional-底层原理)）。

### 本项目的例子

```java
// backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskCommandService.java:24
@Service   // ← 声明"我是 Bean"，容器启动时会创建它
public class ResearchTaskCommandService {
    private final ResearchTaskRepository researchTaskRepository;   // ← 依赖也是 Bean

    // 构造器参数 = "我需要这些 Bean"，容器自动送进来
    public ResearchTaskCommandService(ResearchTaskRepository researchTaskRepository, ...) {
        this.researchTaskRepository = researchTaskRepository;
    }
}
```

---

## 1.2 注册 Bean 的两种方式

### 方式一：类上加 `@Component` 家族（自己写的类）

| 注解 | 语义分层 | 本项目例子 |
|---|---|---|
| `@Service` | 业务层 | `ResearchTaskCommandService.java:24`、`ResearchPlanningService.java:43`、`ResearchTaskOrchestrator.java:52` |
| `@Repository` | 数据访问层 | `JpaTaskEventLogStore.java:17`、`MyBatisTaskEventLogStore.java:17` |
| `@RestController` | Web 接口层 | `ResearchTaskController.java:51`、`ResearchReportController.java:14` |
| `@Component` | 通用组件 | `TaskResponseMapper.java:20`、`WebSearchResearchTool.java:25` |
| `@RestControllerAdvice` | 全局异常处理 | `ApiExceptionHandler.java:14` |
| `@Configuration` | 配置类 | `AsyncExecutionConfiguration.java:11`、`WebCorsConfiguration.java:10` |

> **这四个注解功能完全等价**，区别只在**语义分层**——让人一眼看出这个类属于哪一层（同时也是 AOP 切点的天然分类依据）。

**生效前提**：类必须在**启动类所在包及其子包**下，才会被 `@SpringBootApplication` 的 `@ComponentScan` 扫到。
本项目启动类在 `com.aiexplorer.researchagent`，所有代码都在其下，所以全部能扫到。

### 方式二：`@Configuration` + `@Bean`（第三方类、需手动构造的类）

```java
// backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/AsyncExecutionConfiguration.java:11
@Configuration
public class AsyncExecutionConfiguration {

    @Bean(name = "researchTaskExecutor")            // ← @Bean 标在【方法】上
    public Executor researchTaskExecutor(ExecutionProperties executionProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();   // 自己 new
        executor.setCorePoolSize(executionProperties.corePoolSize());
        executor.setMaxPoolSize(executionProperties.maxPoolSize());
        executor.setQueueCapacity(executionProperties.queueCapacity());
        executor.setThreadNamePrefix("research-task-");
        executor.initialize();
        return executor;                            // ← return 出去，交给容器
    }
}
```

**为什么需要方式二**：
- `ThreadPoolTaskExecutor`、`ObjectMapper`、`RestTemplate` 是**第三方类**，你不能去改它的源码加 `@Component`
- 这些类需要**配置一堆参数**才能用，`@Component` 无法表达"配置过程"

**核心理解**：`@Bean` 标注的是**方法**，Spring 调用这个方法，把**返回值**收进容器。

### 两种方式的对比

| | `@Component` 家族 | `@Configuration` + `@Bean` |
|---|---|---|
| 标在哪 | 类上 | 方法上 |
| 适用 | 自己写的类 | 第三方类 / 需要配置的类 |
| 能否控制构造过程 | ❌ 容器直接 new | ✅ 方法体里随你怎么 new |
| 一个定义产生几个 Bean | 1 个 | 1 个（方法返回一个对象） |
| 本项目例子 | 所有 Service/Controller | 线程池 `researchTaskExecutor` |

> **补充**：`@Configuration` 类本身也是 Bean，且它是**被 CGLIB 增强过的**——在同一个配置类里 `@Bean` 方法互相调用，容器保证拿到的是容器里的单例，而不是真的 new 一个新的（这就是 `proxyBeanMethods` 的由来，默认 `true`）。

---

## 1.3 注入 Bean 的三种方式

| 方式 | 写法 | 评价 |
|---|---|---|
| **构造器注入**（推荐） | 构造器参数 | 依赖不可变（`final`）、启动即暴露循环依赖、易单元测试 |
| Setter 注入 | `@Autowired` 标在 setter 上 | 可选依赖场景，很少用 |
| 字段注入 | `@Autowired` 标在字段上 | **不推荐**：不能 `final`、难测试、掩盖循环依赖 |

### 构造器注入为什么最推荐

```java
// backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskControlService.java
@Service
public class ResearchTaskControlService {

    private final ResearchTaskRepository researchTaskRepository;   // final：构造后不可改
    private final ResearchPlanRepository researchPlanRepository;
    private final TaskEventService taskEventService;

    public ResearchTaskControlService(                            // 单构造器可省略 @Autowired
            ResearchTaskRepository researchTaskRepository,
            ResearchPlanRepository researchPlanRepository,
            TaskEventService taskEventService) {
        this.researchTaskRepository = researchTaskRepository;
        this.researchPlanRepository = researchPlanRepository;
        this.taskEventService = taskEventService;
    }
}
```

三个优势：
1. **依赖不可变**：`final` 字段，对象构造完就不可能被改
2. **依赖完整性**：启动阶段就能发现"少依赖"或"循环依赖"，不会等到运行时才 NPE
3. **可测试**：单元测试里 `new ResearchTaskControlService(mockRepo, ...)` 直接传 mock，不用启动容器

---

## 1.4 同类型多个 Bean 怎么选

当一个接口有多个实现（或同类型多个 Bean）时，Spring 会报 `NoUniqueBeanDefinitionException`。三种解法：

### ① `@Qualifier("bean名字")`（按名指定，最明确）

```java
// backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskOrchestrator.java:78
public ResearchTaskOrchestrator(
        ...
        @Qualifier("researchTaskExecutor") Executor researchTaskExecutor) {
    // 容器里可能有多个 Executor，按名字精确指定
}
```

对应的 Bean 定义处：`AsyncExecutionConfiguration.java:17` 的 `@Bean(name = "researchTaskExecutor")`。

### ② `@Primary`（设默认首选）

```java
@Repository
@Primary
public class JpaTaskEventLogStore implements TaskEventLogStore { }
```

### ③ 注入 `List<T>` / `Map<String,T>`（全部都要）

```java
// ResearchToolRegistry：把 ResearchTool 接口的所有实现一次性收进来
public ResearchToolRegistry(List<ResearchTool> researchTools) {
    researchTools.forEach(tool -> registry.put(tool.getSupportedStepType(), tool));
}
```

本项目 `ResearchTool` 接口有 4 个 `@Component` 实现（`WebSearchResearchTool`、`PageFetchResearchTool`、`CitationExtractResearchTool`、`SummarizeResearchTool`），全部被自动收集进 `List` —— **这是"接口 + 多态 + Bean 容器"最经典的玩法，也是策略模式的零配置实现**。

| 解法 | 场景 |
|---|---|
| `@Qualifier` | 明确知道要哪一个 |
| `@Primary` | 有一个是默认，其余用 `@Qualifier` 指定 |
| `List<T>` | 全都要（策略链、责任链） |

---

## 1.5 Bean 的作用域

| 作用域 | 含义 | 何时创建 | 适用 |
|---|---|---|---|
| **singleton**（默认） | 整个容器**一个**实例 | 容器启动时 | 无状态对象：Service、Repository、Controller |
| prototype | 每次注入/`getBean` **都新建** | 每次获取时 | 有状态的短命对象（很少用） |
| request | 每个 HTTP 请求一个 | 请求开始 | Web 请求上下文（罕见） |
| session | 每个会话一个 | 会话建立 | 用户登录态（罕见） |
| application | 整个 ServletContext 一个 | 应用启动 | 全局配置 |

```java
@Component
@Scope("prototype")
public class SomeStatefulObject { }
```

### 单例 Bean 的线程安全（必考）

**单例 Bean 本身不保证线程安全**——一个实例被所有请求共享，并发调用同一个方法。

**正确做法**：**Bean 设计成无状态**（没有可变的成员变量，状态全在方法参数和返回值里）。

```java
// ✅ 无状态：安全，本项目所有 Service 都是这样
@Service
public class ResearchTaskQueryService {
    private final ResearchTaskRepository repository;   // final 依赖，不可变 → 安全

    public TaskDetailResponse getTaskDetail(UUID taskId) {
        return repository.findById(taskId)...;          // 状态只在局部变量里
    }
}

// ❌ 有状态：灾难，所有请求共享这个计数器
@Service
public class BadService {
    private int counter = 0;              // 多线程同时 ++ → 数据错乱
    private ResearchTaskEntity currentTask;  // A 请求设的被 B 请求覆盖
}
```

需要存请求相关状态时，用 `ThreadLocal`、方法参数，或直接改用 `prototype`。

---

## 1.6 Bean 的完整生命周期

```
① 扫描/解析配置 → 生成 BeanDefinition（"配方"，还不是对象）
        ↓
② 实例化 Instantiation：调用构造器 new 出对象（此时属性全是默认值）
        ↓
③ 属性填充 Populate：依赖注入（@Autowired / 构造器参数 / @Value）
        ↓
④ Aware 回调：BeanNameAware、BeanFactoryAware、ApplicationContextAware
        ↓
⑤ BeanPostProcessor【前置】 postProcessBeforeInitialization
        ↓
⑥ 初始化 InitializingBean.afterPropertiesSet() → @PostConstruct 方法
        ↓
⑦ BeanPostProcessor【后置】 postProcessAfterInitialization
        ★★★ 就是在这里创建 AOP 代理对象！★★★
        ↓
⑧ Bean 就绪，放入单例池（Singleton Cache），可被注入使用
        ↓
⑨ 容器关闭 → @PreDestroy → DisposableBean.destroy()
```

### 三个关键认知

**① 第 ⑦ 步是 AOP 的织入点**
`AnnotationAwareAspectJAutoProxyCreator` 就是一个 `BeanPostProcessor`：在后置处理时判断这个 Bean 是否匹配切点，匹配就返回**代理对象**替代原始对象。所以**容器里最终存的是代理，不是你的原始对象**——这就是 `@Transactional` 能生效的根本原因。

**② `@PostConstruct` 的用途**
对象构造完成、依赖注入完毕后的初始化动作，项目常用于：加载字典到内存、预热缓存、启动后台线程、校验配置。

```java
@PostConstruct
public void init() {
    // 依赖已注入完毕，可以安全使用 this.xxx
}

@PreDestroy
public void cleanup() {
    // 容器关闭时释放资源
}
```

对比：**构造器**执行时依赖还没注入（不能用 `this.xxx`），**`@PostConstruct`** 里依赖已就绪。

**③ 常见扩展点**

| 扩展点 | 时机 | 典型用途 |
|---|---|---|
| `BeanPostProcessor` | 每个 Bean 初始化前后 | ★ AOP 代理、`@Autowired` 处理 |
| `BeanFactoryPostProcessor` | BeanDefinition 加载后、实例化前 | 修改"配方"（占位符解析） |
| `InitializingBean` / `@PostConstruct` | 属性填充后 | 初始化 |
| `ApplicationRunner` / `CommandLineRunner` | 容器**完全就绪后** | 启动任务（本项目 `PersistenceModeDemoRunner` 实现它） |
| `ApplicationListener` | 各种 Spring 事件 | 事件驱动 |

---

## 1.7 条件装配

**同一个接口多个实现，按配置决定启用哪一个** —— 这是企业项目最常见的 Bean 技巧。

### 本项目真实案例：JPA / MyBatis 双 Store 切换

```java
// backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/store/jpa/JpaTaskEventLogStore.java:17
@Repository
@ConditionalOnProperty(name = "app.persistence.mode", havingValue = "jpa", matchIfMissing = true)
public class JpaTaskEventLogStore implements TaskEventLogStore { }

// backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/store/mybatis/MyBatisTaskEventLogStore.java:17
@Repository
@ConditionalOnProperty(name = "app.persistence.mode", havingValue = "mybatis")
public class MyBatisTaskEventLogStore implements TaskEventLogStore { }
```

**效果**：`app.persistence.mode=jpa` → 只注册 JPA 版；`=mybatis` → 只注册 MyBatis 版；不配 → `matchIfMissing = true` 让 JPA 版成为默认（**零配置也能跑**）。
调用方只依赖 `TaskEventLogStore` 接口，切换实现**不改一行业务代码**。

### 条件注解全家

| 注解 | 含义 | 本项目例子 |
|---|---|---|
| `@ConditionalOnProperty` | 配置值匹配才注册 | 上面两个 Store |
| `@Profile("dev")` | 激活指定环境才注册 | `PersistenceModeDemoRunner.java:23`（只在 dev 跑演示） |
| `@ConditionalOnClass` | classpath 存在某类 | Boot 自动配置的基石（有 `DataSource` 才配数据源） |
| `@ConditionalOnMissingBean` | 容器里**没有**同类 Bean 才注册 | ★ **用户自定义 Bean 永远优先于自动配置**的原因 |
| `@ConditionalOnBean` | 存在某 Bean 才注册 | 依赖型配置 |
| `@ConditionalOnWebApplication` | Web 环境才注册 | MVC 相关配置 |

---

## 1.8 循环依赖

### 是什么

```
A 的构造器要 B  →  B 的构造器要 A  →  死锁
```

```java
@Service
public class A { public A(B b) { } }      // 构造器注入：Spring 无法解决
@Service
public class B { public B(A a) { } }
```

Spring 会直接抛 `BeanCurrentlyInCreationException`（**启动失败**）。

> 注意：字段注入（`@Autowired` 标字段）时 Spring 能用"三级缓存"提前暴露半成品对象来解决，但**构造器注入不行**。这不是字段注入的优点——它只是把设计问题掩盖到运行时。

### 正确解法（按推荐度）

1. **重新设计分层**（推荐）：A 和 B 互相依赖，几乎一定是**职责划分错了**——把公共逻辑抽到 C，A、B 都依赖 C
2. **用事件解耦**：A 发事件，B 监听（ApplicationContext 事件或 MQ）
3. **`@Lazy`**：`@Lazy private final B b;` 注入代理，用到时才真正创建（兜底手段）
4. **Setter 注入**：打破构造期死锁（次选）

> 注意：Spring Boot 2.6 起 `spring.main.allow-circular-references` 默认 `false`，即**默认禁止**循环依赖。

---

# 第二部分：事务

## 2.1 事务是什么（ACID）

**事务 = 一组数据库操作，要么全部成功，要么全部失败**（all or nothing）。

| 特性 | 全称 | 含义 |
|---|---|---|
| **A** | Atomicity 原子性 | 一组操作不可分割，要么全做要么全不做 |
| **C** | Consistency 一致性 | 事务前后数据都满足业务约束（余额不为负） |
| **I** | Isolation 隔离性 | 并发事务之间互不干扰 |
| **D** | Durability 持久性 | 提交后即使宕机也不丢 |

### 本项目的真实例子

```java
// backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskCommandService.java:53
@Transactional
public TaskSummaryResponse createTask(CreateResearchTaskRequest request, String createdBy) {
    ResearchTaskEntity savedTask = researchTaskRepository.save(task);          // ① 插入任务
    researchPlanningService.generateInitialPlan(savedTask.getId());            // ② 生成计划+步骤
    return taskResponseMapper.toTaskSummary(plannedTask);
}
```

**没有事务会怎样**：① 成功、② 失败 → 数据库里躺着一个**没有计划的孤儿任务**（脏数据）。
**有事务**：② 失败 → ① 一起回滚，数据库干净。

```java
// backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskControlService.java:51
@Transactional
public void confirmPlan(UUID taskId, boolean approved, String responseMessage) {
    latestPlan.setStatus(...);        // 改计划
    confirmation.setStatus(...);      // 改确认记录
    task.setStatus(...);              // 改任务状态
    researchPlanRepository.save(latestPlan);
    humanConfirmationRepository.save(confirmation);
    researchTaskRepository.save(task);
    // 三张表的修改，任何一个失败全部回滚
}
```

---

## 2.2 编程式 vs 声明式

| | 编程式事务 | 声明式事务（`@Transactional`） |
|---|---|---|
| 写法 | 手动写 `begin/commit/rollback` | 一个注解 |
| 侵入性 | 高，业务代码被事务语句淹没 | 低，业务代码干净 |
| 粒度控制 | 精确到代码块 | 精确到方法 |
| 推荐度 | 特殊场景（细粒度、条件化） | **日常 95% 场景** |

```java
// 编程式（啰嗦）
transactionTemplate.execute(status -> { ... });

// 声明式（推荐）
@Transactional
public void createTask(...) { ... }
```

---

## 2.3 @Transactional 底层原理

**一句话：`@Transactional` 是 AOP 代理实现的，事务代码全在代理层，你的方法体里没有。**

### 完整调用链

```
外部调用 researchTaskCommandService.createTask(...)
   ↓
【代理对象】TransactionInterceptor.invoke()
   ↓
   ① 通过 TransactionManager 获取数据库连接
   ② 关闭自动提交 conn.setAutoCommit(false)
   ③ 把连接绑定到当前线程（ThreadLocal）—— 这是同一事务共用一条连接的关键
   ↓
   ④ 执行你的目标方法（业务 SQL 全用这条连接）
   ↓
   ⑤ 成功 → conn.commit()
      抛异常 → conn.rollback()
   ↓
   ⑥ 释放连接、清除 ThreadLocal 绑定
```

### 关键实现细节

**① 切点就是 `@annotation`**
事务切面的切点等价于：
```java
@Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
```
所以注解必须**被代理层看到**——这是所有失效场景的根源。

**② 连接绑定到 ThreadLocal**
`TransactionSynchronizationManager` 用 `ThreadLocal` 保存"当前事务的连接"。
这解释了两件事：
- 同一个事务里所有 DAO 操作自动共用同一条连接（所以才会一起提交/回滚）
- **新开线程 = 新 ThreadLocal = 拿不到连接 = 事务传播不过去**（见 [2.7](#27-七大失效场景) 场景⑤）

**③ 为什么只对 Bean 生效**
代理对象是容器在 Bean 生命周期第 ⑦ 步（`BeanPostProcessor` 后置处理）生成的。自己 `new` 的对象没走这个流程，不会有任何代理。

---

## 2.4 传播行为 propagation

**定义：当前方法被调用时，如果外层已经存在事务，该怎么办。**

| 传播行为 | 含义 | 场景 |
|---|---|---|
| **REQUIRED**（默认） | 有事务就加入，没有就新建 | **绝大多数场景**，保持"共用一个事务" |
| **REQUIRES_NEW** | **挂起外层事务，另起一个独立事务** | 日志/审计：业务失败回滚，但日志必须留下 |
| SUPPORTS | 有就加入，没有就非事务运行 | 查询方法（很少用） |
| NOT_SUPPORTED | 挂起事务，以非事务方式执行 | 明确不需要事务的批量查询 |
| MANDATORY | 必须在已有事务中，没有就报错 | 强制要求调用方开事务 |
| NEVER | 必须没有事务，有就报错 | 严格禁止事务 |
| NESTED | 嵌套事务：外层回滚内层也回滚，内层可单独回滚（保存点） | 少用，需数据库支持 |

### REQUIRED vs REQUIRES_NEW（最常考的对比）

```java
// 场景：记录操作日志。业务失败要回滚，但日志必须留存
@Service
public class AuditService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)   // ← 独立事务
    public void recordLog(String message) {
        auditRepository.save(new AuditLog(message));
    }
}

@Service
public class OrderService {
    @Transactional                       // 外层事务
    public void createOrder() {
        auditService.recordLog("创建订单");  // 独立提交，不受外层回滚影响
        // ... 后续业务抛异常 → 外层回滚，但上面的日志已提交，留下来了
    }
}
```

| | REQUIRED（默认） | REQUIRES_NEW |
|---|---|---|
| 是否共用连接 | ✅ 共用一条 | ❌ 新开一条 |
| 外层回滚 | 内层一起回滚 | **内层不受影响** |
| 内层回滚 | 外层也被标记回滚 | 外层不受影响（除非异常继续抛出） |
| 数据库连接占用 | 1 条 | 2 条（连接池压力 ×2） |

### 本项目的用法

本项目**全部使用默认的 `REQUIRED`**，这是正确的：

```java
// ResearchTaskCommandService.createTask（事务）
//   → researchPlanningService.generateInitialPlan（事务，默认 REQUIRED）
// 效果：跨 Bean 调用，内层【加入】外层事务 → 计划生成失败，任务创建一起回滚
// 这正是"要么都成功要么都失败"的期望语义
```

对应代码：`ResearchTaskCommandService.java:72` 调用 `ResearchPlanningService.java:86`。

---

## 2.5 隔离级别 isolation

**定义：并发事务之间互相可见到什么程度。**

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 性能 | 数据库默认 |
|---|---|---|---|---|---|
| READ_UNCOMMITTED 读未提交 | ❌ 会 | ❌ 会 | ❌ 会 | 最高 | — |
| **READ_COMMITTED 读已提交** | ✅ 防 | ❌ 会 | ❌ 会 | 高 | **PostgreSQL、SQL Server、Oracle** |
| **REPEATABLE_READ 可重复读** | ✅ 防 | ✅ 防 | ❌ 会（MySQL InnoDB 实际可防） | 中 | **MySQL InnoDB** |
| SERIALIZABLE 串行化 | ✅ 防 | ✅ 防 | ✅ 防 | 最低 | — |

三个并发问题：
- **脏读**：读到别的事务**还没提交**的数据（对方可能回滚）
- **不可重复读**：同一事务内两次读同一行，值变了（对方已提交 UPDATE）
- **幻读**：同一事务内两次查询，行数变了（对方已提交 INSERT/DELETE）

### 本项目的选择

```java
@Transactional(isolation = Isolation.DEFAULT)   // ← 默认，即"用数据库的默认级别"
```

**这就是最佳实践**：本项生产用 **PostgreSQL（默认 READ_COMMITTED）**，开发用 H2 的 PostgreSQL 兼容模式，**两边一致**。不建议在代码里硬编码隔离级别——换数据库时语义可能不同，且会引入额外的锁开销。

---

## 2.6 其他属性（rollbackFor / readOnly / timeout）

### ① `rollbackFor`：最重要的一个坑

**Spring 默认只在抛出 `RuntimeException` 和 `Error` 时回滚，受检异常（Checked Exception）不回滚！**

```java
@Transactional
public void doSomething() throws IOException {
    // 抛出 IOException（受检异常）→ 默认【不回滚】，数据已提交！
}
```

**正确写法**：

```java
@Transactional(rollbackFor = Exception.class)   // ← 建议养成习惯，任何异常都回滚
public void doSomething() throws IOException { }
```

| 配置 | 回滚的异常 |
|---|---|
| 默认 | `RuntimeException` + `Error` |
| `rollbackFor = Exception.class` | 所有异常（**推荐**） |
| `noRollbackFor = XxxException.class` | 指定不回滚 |

> 原理：事务拦截器判断异常类型决定是否 `rollback()`。这也是为什么**绝不能在 Service 里把异常 try-catch 掉却不抛出**——异常传不到代理层，代理就认为"执行成功"提交了。

### ② `readOnly = true`：查询专用

```java
// backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskQueryService.java:42
@Transactional(readOnly = true)
public TaskDetailResponse getTaskDetail(UUID taskId) { ... }
```

作用：
- 提示 Hibernate 跳过脏检查（不对比快照决定是否 UPDATE）→ 略省性能
- 底层可路由到从库（配合读写分离时）
- **语义声明**：明确告诉读代码的人"这个方法不写库"

⚠️ 它**不是锁**，也不会阻止你写——写了会抛异常或行为未定义，别指望它当保护。

### ③ `timeout`：超时自动回滚

```java
@Transactional(timeout = 30)   // 单位：秒，超过就强制回滚，防长事务拖垮连接池
```

### 完整属性表

| 属性 | 默认 | 说明 |
|---|---|---|
| `propagation` | `REQUIRED` | 传播行为 |
| `isolation` | `DEFAULT`（用库的） | 隔离级别 |
| `timeout` | -1（不超时） | 超时秒数 |
| `readOnly` | false | 只读事务 |
| `rollbackFor` | `RuntimeException`/`Error` | 触发回滚的异常类型 |
| `noRollbackFor` | 无 | 不触发回滚的异常类型 |

---

## 2.7 七大失效场景

### ① 方法不是 `public`

```java
@Transactional
private void inner() { }      // ❌ 不生效（CGLIB 无法重写 private）
```
`protected`/包级可见同样不生效（CGLIB 只能重写 public/protected 且非 final，Spring 事务切面只处理 public）。

### ② 同类内部 `this.` 自调用（最隐蔽）

```java
@Service
public class TaskService {
    @Transactional
    public void methodA() {
        this.methodB();       // ❌ 绕过代理，methodB 的事务不生效
    }

    @Transactional
    public void methodB() { }
}
```

**口诀：代理只拦"从外面打进来的电话"，不拦"屋子里面自己喊"。**

解法（推荐度递减）：拆到另一个 Bean → 注入自身代理 → `AopContext.currentProxy()`。
**本项目的正确示范**：`ResearchTaskCommandService.createTask` 调的是**另一个 Bean** 的
`researchPlanningService.generateInitialPlan`，跨 Bean 走代理，事务正常。

### ③ 自己 `new` 的对象

```java
TaskService service = new TaskService();
service.methodA();      // ❌ 不是容器 Bean，没有代理，注解完全被无视
```

### ④ 抛了受检异常（没配 `rollbackFor`）

```java
@Transactional
public void doIt() throws IOException {
    throw new IOException();     // ❌ 默认不回滚，数据已提交
}
```

### ⑤ 异常被自己 `catch` 掉

```java
@Transactional
public void doIt() {
    try {
        repository.save(entity);
        int i = 1 / 0;
    } catch (Exception e) {
        log.error("出错了", e);      // ❌ 异常没抛出去，代理认为成功 → 提交
    }
}
```

**正确做法**：`catch` 后必须重新抛出，或手动标记回滚：
```java
catch (Exception e) {
    log.error("出错了", e);
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();  // 或 throw e
}
```

### ⑥ 异常抛到新线程里去了

```java
@Transactional
public void doIt() {
    executor.execute(() -> {
        repository.save(entity);     // ❌ 新线程，ThreadLocal 里没有事务连接
        throw new RuntimeException(); // ❌ 主线程的代理感知不到，事务照常提交
    });
}
```

**原因**：事务连接绑定在 `ThreadLocal` 上，**跨线程传不过去**。
**本项目注意点**：`ResearchTaskControlService.java:103` 的 `researchTaskOrchestrator.startExecution(taskId)` 在事务方法内提交异步任务——异步线程读任务状态时，主事务可能**尚未提交**，存在读到旧状态的隐患。处理办法：在事务**提交之后**再触发（用 `TransactionSynchronizationManager.registerSynchronization` 的 `afterCommit` 回调）。

### ⑦ 数据库引擎不支持事务

MySQL 的 **MyISAM 引擎不支持事务**，表引擎必须是 **InnoDB**。
本项目用 PostgreSQL / H2（PG 模式），无此问题。

### 速查表

| 场景 | 是否生效 | 关键原因 |
|---|---|---|
| `public` 方法、跨 Bean 调用 | ✅ | 走代理 |
| `private` / `final` / `static` 方法 | ❌ | 无法重写 |
| 同类 `this.` 自调用 | ❌ | 绕过代理 |
| `new` 出来的对象 | ❌ | 不是 Bean，无代理 |
| 抛受检异常且未配 `rollbackFor` | ❌ 不回滚 | 默认只回滚运行时异常 |
| 异常被 catch 掉不抛 | ❌ 不回滚 | 代理以为成功 |
| 异常发生在新线程 | ❌ 不回滚 | ThreadLocal 不跨线程 |
| 表引擎 MyISAM | ❌ | 数据库不支持 |

---

## 2.8 编程式事务 TransactionTemplate

声明式做不到"**同一个方法内，一部分要事务、一部分不要**"时使用。

```java
@Service
public class BatchImportService {

    private final TransactionTemplate transactionTemplate;

    public BatchImportService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void importBatch(List<Data> dataList) {
        for (Data data : dataList) {
            // 每条数据一个独立小事务：单条失败不影响其他条
            transactionTemplate.execute(status -> {
                repository.save(data);
                return null;
            });
        }
    }
}
```

**适用场景**：批量导入（每批一个事务，避免超长事务）、需要根据条件动态决定是否开事务。
**日常业务请继续用 `@Transactional`**，别为了"可控"把代码写得又臭又长。

---

# 第三部分：结合本项目

## 3.1 事务使用全景

| 文件:行 | 方法 | 类型 | 说明 |
|---|---|---|---|
| `application/service/ResearchTaskCommandService.java:53` | `createTask` | 读写 | 建任务 + 生成计划，同生共死 |
| `application/service/ResearchPlanningService.java:86` | `generateInitialPlan` | 读写 | 加入外层事务（REQUIRED） |
| `application/service/ResearchTaskControlService.java:51` | `confirmPlan` | 读写 | 计划 + 确认记录 + 任务状态，三表一致 |
| `application/service/ResearchTaskControlService.java:110` | `pauseTask` | 读写 | 改状态 + 记录事件 |
| `application/service/ResearchTaskControlService.java:126` | `resumeTask` | 读写 | 改状态 + 记录事件 + 触发执行 |
| `application/service/ResearchTaskControlService.java:145` | `cancelTask` | 读写 | 改状态 + 记录事件 |
| `application/service/ResearchReportAssemblyService.java:46` | 报告生成 | 读写 | 报告落库 |
| `application/service/ResearchTaskQueryService.java:42` | 列表查询 | **只读** | `readOnly = true` |
| `application/service/ResearchTaskQueryService.java:53` | 详情查询 | **只读** | `readOnly = true` |
| `application/service/ResearchReportQueryService.java:30` | 报告查询 | **只读** | `readOnly = true` |
| `application/service/TaskActivityQueryService.java:34` | 时间线查询 | **只读** | `readOnly = true` |
| `application/service/TaskActivityQueryService.java:44` | 事件查询 | **只读** | `readOnly = true` |

> 路径前缀统一为 `backend/src/main/java/com/aiexplorer/researchagent/`。

**两个优点值得肯定**：
1. **查询方法全部标了 `readOnly = true`** —— 语义清晰，也是好习惯
2. **写操作按"业务用例"划分事务边界**（一个用例 = 一个 Service 方法 = 一个事务），粒度正确

## 3.2 Bean 使用全景

| 技巧 | 本项目位置 |
|---|---|
| `@Component` 家族分层 | 所有 Service（`@Service`）、Controller（`@RestController`）、Store（`@Repository`） |
| `@Configuration + @Bean` | `infrastructure/config/AsyncExecutionConfiguration.java:17`（线程池） |
| 构造器注入 | 全部 Service 与 Controller（单构造器，无 `@Autowired`） |
| `@Qualifier` 按名注入 | `application/service/ResearchTaskOrchestrator.java:78`（指定 `researchTaskExecutor`） |
| `List<T>` 收集全部实现 | `application/service/ResearchToolRegistry.java`（收集 4 个 `ResearchTool`） |
| `@ConditionalOnProperty` 条件装配 | `JpaTaskEventLogStore.java:18` / `MyBatisTaskEventLogStore.java:18`（双 Store 切换） |
| `@Profile("dev")` | `infrastructure/persistence/store/PersistenceModeDemoRunner.java:23` |
| `@ConfigurationProperties` | `infrastructure/config/LlmProperties.java:8`、`ExecutionProperties.java:8` |
| 接口 + 多实现 | `TaskEventLogStore`（JPA/MyBatis）、`ResearchTool`（4 个工具） |

## 3.3 两个值得改进的点

**① 事务注解建议显式加 `rollbackFor`**

目前全部是裸 `@Transactional`，一旦方法里抛出受检异常（如 IO 异常、JSON 解析异常）就不会回滚。建议统一改成：

```java
@Transactional(rollbackFor = Exception.class)
```

**② 异步触发应在事务提交之后**

`ResearchTaskControlService.confirmPlan`（`:103`）与 `resumeTask`（`:139`）在事务方法**内部**调用
`researchTaskOrchestrator.startExecution(taskId)`。ASYNC 模式下任务被丢进独立线程池，
异步线程极可能在主事务**提交之前**就去读任务状态，读到旧值。

推荐写法：

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {          // 事务确认提交后才触发
            researchTaskOrchestrator.startExecution(taskId);
        }
    });
```

---

# 第四部分：速查与口诀

## 4.1 对比速查表

| 问题 | 答案 |
|---|---|
| Bean 和 `new` 的对象区别 | Bean 由容器创建/注入/管理，有 AOP 增强；`new` 的没有 |
| `@Component` 和 `@Bean` 区别 | 前者标类（自己的类）；后者标方法（第三方的类、需配置的类） |
| 为什么推荐构造器注入 | 依赖可 `final`、启动即暴露问题、易单元测试 |
| 单例 Bean 线程安全吗 | 本身不安全；**设计成无状态就安全** |
| Bean 在哪一步被 AOP 代理 | 生命周期第 ⑦ 步：`BeanPostProcessor` 后置处理 |
| AOP 在哪一步织入 | 同上，运行期生成代理对象（默认 CGLIB） |
| 事务为什么只对 Bean 生效 | 事务靠 AOP 代理，代理只对容器 Bean 生成 |
| 默认回滚哪些异常 | `RuntimeException` + `Error`（**受检异常不回滚**） |
| 默认传播行为 | `REQUIRED`（有就加入，没有就新建） |
| 默认隔离级别 | `DEFAULT`（用数据库的） |
| 同类自调用事务失效吗 | 失效（`this.` 绕过代理） |
| 事务能跨线程吗 | 不能（连接绑在 `ThreadLocal`） |
| 条件装配用什么 | `@ConditionalOnProperty`、`@Profile`、`@ConditionalOnMissingBean` |

## 4.2 口诀

1. **Bean = 容器管理的对象**；注册靠 `@Component`（自己的类）或 `@Configuration+@Bean`（第三方的类），获取靠构造器注入
2. **注入三选一**：构造器（推荐）> Setter > 字段（不推荐）
3. **同类型多 Bean**：`@Qualifier` 按名、`@Primary` 设默认、`List<T>` 全收集
4. **单例 Bean 要无状态**，可变成员变量是并发灾难
5. **Bean 生命周期第 ⑦ 步 = AOP 织入点**，容器里存的是代理对象
6. **事务 = AOP 代理**：BEGIN/COMMIT 在代理层，你的方法体里没有
7. **事务三默认**：传播 `REQUIRED`、隔离 `DEFAULT`、只回滚运行时异常
8. **`rollbackFor = Exception.class` 是保命习惯**
9. **事务失效四大坑**：非 public、`this.` 自调用、异常被 catch、新线程抛异常
10. **查询加 `readOnly = true`**，写操作按"一个用例一个事务"划边界

## 4.3 一图流

```
【事务生效的完整链路】

Controller
   ↓ 注入的是【代理对象】
ResearchTaskCommandService$$SpringCGLIB$$0
   ↓
TransactionInterceptor.invoke()
   ├─ ① 取连接、关自动提交
   ├─ ② 连接绑定 ThreadLocal
   ├─ ③ 调目标方法 → generateInitialPlan（跨 Bean，走代理，加入同一事务）
   ├─ ④ 成功 → commit ／ 异常 → rollback（仅 RuntimeException/Error，除非配 rollbackFor）
   └─ ⑤ 释放连接、清 ThreadLocal

【失效只需断掉任意一环】
  new 的对象 → 没有代理 → 断在第 ① 步前
  this. 自调用 → 绕过代理 → 断在第 ① 步前
  private/final → 无法生成代理 → 断在第 ① 步前
  异常被 catch → 代理不知情 → 断在第 ④ 步（照常提交）
```
