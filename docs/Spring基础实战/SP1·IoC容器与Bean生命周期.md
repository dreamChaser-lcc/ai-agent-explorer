# SP1 · IoC 容器与 Bean 生命周期

> **课程**：Spring 基础实战 · 模块一（Spring Framework 核心）
> **已会**：构造器注入、`@Bean`/`@Component`、"申请书+外包工"模型（[J7·注解体系](../Java基础实战/J7·注解体系.md)）、场景⑧ 的 `LlmClientConfiguration` 实战（[⑧下游调用治理](../业务场景实战/⑧下游调用治理.md) §三）
> **本次**：容器启动到底做了什么、**Bean 生命周期五阶段**、作用域、多 bean 冲突（@Primary/@Qualifier）、**循环依赖与三级缓存**
> **对标**：廖雪峰 22.1「IoC 容器」
> **状态**：✅ 讲义完成（2026-10-21）

---

## 零、前置知识速查（复习专用 · 完整讲解见链接）

本课依赖的旧知识，核心结论在此**完整写明**；要深入再点链接。

### 速查 1 · "申请书 + 外包工"模型（来自 [J7·注解体系](../Java基础实战/J7·注解体系.md)）

- **注解 = 申请书**：`@Component`/`@Service`/`@Configuration` 都是在向 Spring 容器递交"我要成为一个 bean"的申请；
- **框架 = 执行者**：应用启动时 Spring 扫描所有申请书，创建对象（bean）并按需互相注入；
- **你从来不写 `new`**：依赖靠构造器参数"声明需要"，容器启动时自动塞进来。

### 速查 2 · AOP 审计切面（来自 [⑤AOP操作审计](../业务场景实战/⑤AOP操作审计.md)）

- 项目里有一个自定义注解 `@AuditLog` + 一个切面类 `AuditLogAspect`；
- 标了 `@AuditLog` 的方法被调用时，切面会**自动在方法前后**插入"记录谁、何时、改了什么"的审计日志——**业务方法代码里一行都不用写**；
- SP2 会讲这背后的原理（动态代理）。

### 速查 3 · 事务（来自 [②任务状态机](../业务场景实战/②任务状态机.md)）

- `@Transactional` 标在方法上：方法执行前开启数据库事务，正常返回则提交、抛异常则回滚；
- 场景②用它保证"任务状态 + 步骤 + 事件"要么全成功要么全回滚；
- SP4 会讲传播行为与失效场景。

### 速查 4 · 配置绑定（来自 [⑧下游调用治理](../业务场景实战/⑧下游调用治理.md) §十三 Q5）

- `@ConfigurationProperties(prefix = "app.llm")` 的 record：Spring 启动时把 yml 里 `app.llm.*` 的值按字段名填进对象；
- 键名支持松散绑定（`base-url` ↔ `baseUrl`）；yml 没写的 key 值为 null/0/false。

### 速查 5 · 全局异常处理（来自 [①统一API规范](../业务场景实战/①统一API规范.md)）

- `@RestControllerAdvice` 类里的 `@ExceptionHandler(XxxException.class)` 方法：全局兜底转换——任何 Controller 抛出该异常，自动转成统一的 JSON 错误响应（HTTP 状态码 + 错误体）。

---

## 一、IoC 是什么：从"自己 new"到"容器给你"

### 1.1 前端类比

| 前端世界 | Spring 世界 |
|---|---|
| Angular 的 `Injectable` + 构造器注入 | `@Component` + 构造器注入 |
| NestJS 的 Module/Provider | `@Configuration` + `@Bean` |
| Vuex/Pinia 的全局 store | singleton Bean |
| `new Component()` 手动实例化 | prototype Bean（每次获取都新建） |
| ES Module 循环 import | Bean 循环依赖（Spring 有三级缓存解法） |

### 1.2 控制反转的本质

```java
// 没有 IoC：类自己创建依赖——耦合死了，没法替换/测试
public class SummarizeResearchTool {
    private LlmClient client = new LangChain4jLlmClient();  // ← 写死
}

// 有 IoC：类只声明"我需要什么"，容器决定"给你哪个"
public class SummarizeResearchTool {
    private final LlmClient client;
    public SummarizeResearchTool(LlmClient client) { this.client = client; }  // ← 容器注入
}
```

**"控制"指的是"对象创建权的反转"**：从类自己 new，反转为容器创建再注入。收益三个：可替换（换 LangChain4j 实现上层零改）、可测试（注入 mock）、可管理（生命周期统一调度）。

---

## 二、容器启动流程：`refresh()` 五步简化版

`SpringApplication.run()` 最终调用 `AbstractApplicationContext.refresh()`。源码几十个步骤，抓住五个关键阶段即可：

```text
① 准备环境与环境属性            （读 application.yml、环境变量——SP6 详讲）
② 扫描并注册 BeanDefinition     （扫描 @Component/@Configuration，解析 @Bean 方法）
     ↓ 注意：这一步只是"登记申请书"，还没有实例化任何业务 Bean！
③ 注册 BeanPostProcessor        （生命周期回调的"监听员"先就位——所以要早于业务 Bean）
④ 【实例化所有 singleton Bean】  ← Bean 生命周期从这里开始（见第三节）
⑤ 发布启动完成事件 / ready      （ApplicationRunner 之类在这里执行）
```

**记住一个反直觉的点**：`@Component` 扫描 ≠ 立即创建对象。启动时先全部登记成 `BeanDefinition`（"申请书"），然后在第 ④ 阶段统一实例化。

**项目证据**（`logs/llm-search.log`）：

```text
16:39:45  LLM 使用【LangChain4j】实现：baseUrl=...    ← 这是 LlmClient bean 创建时打的日志（阶段④）
16:39:49  Started ResearchAgentApplication            ← 阶段⑤ 完成
```

两行日志相隔约 4 秒——这正是阶段④"批量实例化所有 singleton"的耗时。

---

## 三、Bean 生命周期五阶段（本课核心）

```text
① 实例化（Instantiation）        调构造器，对象诞生（但依赖可能还没注入完）
② 属性注入（Populate）           @Autowired / @Value 填充字段
③ 初始化（Initialization）       @PostConstruct → InitializingBean.afterPropertiesSet() → init-method
④ 使用中（In Use）               singleton 常驻容器，供所有调用方共享
⑤ 销毁（Destruction）            容器关闭：@PreDestroy → DisposableBean.destroy() → destroy-method
```

**三条规则**：

1. **顺序固定**：构造器 → 字段注入 → `@PostConstruct`。所以在构造器里**不要用**被 `@Value` 注入的字段（那时还是 null）——这是我们场景⑧ 里 `LlmProperties` 兜底判空的原因之一；
2. **初始化回调三兄弟**（按序执行），常用 `@PostConstruct`（JSR-250 标准注解，与 Spring 解耦）；
3. **`@PreDestroy` 在容器正常关闭时触发**（Ctrl+C / kill -15），`kill -9` 不会。

### 3.1 动手实验（项目里已加演示 Bean：`BeanLifecycleDemo`）

启动后看日志：

```text
[SP1-生命周期] ① 实例化：构造器执行
[SP1-生命周期] ③ 初始化完成：@PostConstruct，applicationName=research-agent-backend
（应用运行中……）
Ctrl+C 关闭
[SP1-生命周期] ⑤ 销毁：@PreDestroy
```

**注意②去哪了**：演示 Bean 用的是构造器注入（ObjectMapper 直接在构造器参数里），所以"属性注入"和"实例化"合并发生。只有**字段注入**（`@Value` 直接标在字段上）才会表现为独立的第②步。

### 3.2 前端类比

```text
React 组件：constructor → render → componentDidMount → … → componentWillUnmount
Spring Bean：构造器    → 注入  → @PostConstruct      → … → @PreDestroy
```

`componentDidMount` 里才敢安全用 props/state，正如 `@PostConstruct` 里才敢安全用注入的依赖。

---

## 四、作用域：singleton 与 prototype

| 作用域 | 行为 | 前端类比 | 注意 |
|---|---|---|---|
| `singleton`（**默认**） | 整个容器一个实例 | Pinia store / 模块级单例 | 有状态的 Bean 要小心并发（我们所有 Service 都是单例，靠无状态设计保证安全） |
| `prototype` | 每次获取都新建 | `new Component()` | **容器只负责创建，不负责销毁回调**（用完即弃） |

**单例 + 无状态 = 线程安全**。检查项目里的 Service：`SummarizeResearchTool` 等全部只有 `final` 不可变依赖，没有可变实例字段——这就是为什么多线程（Kafka 消费者、HTTP 请求）可以并发调用而不用加锁。

 prototype 的坑：注入进 singleton 时只注入**一次**（之后不再新建）——需要动态获取要用 `ObjectProvider` 或 `@Lookup`（了解即可）。

---

## 五、多 Bean 冲突：`@Primary` 与 `@Qualifier`

**场景**：接口 `LlmClient` 有两个实现 bean 时，构造器注入 `LlmClient client` → 启动直接报错：

```text
NoUniqueBeanDefinitionException:
  expected a single bean but found 2: langChain4jLlmClient, httpClientLlmClient
```

**三种解法**：

| 解法 | 写法 | 适用 |
|---|---|---|
| `@Primary` | 标在其中一个 bean 上：默认选它 | "有一个默认实现，其余是特殊情况" |
| `@Qualifier("名字")` | 注入处指定：`@Qualifier("httpClientLlmClient")` | "这次我明确要某个实现" |
| 按名匹配 | 参数名 = bean 名 | 隐晦，不推荐 |

**项目现状**：我们用"配置条件选择"（[⑧下游调用治理](../业务场景实战/⑧下游调用治理.md) §三：`LlmClientConfiguration` 里按 `clientImpl` 返回一个），避免了多 bean 冲突——这也是一种解法（工厂模式 + 条件装配）。三种思路对比着记。

---

## 六、循环依赖与三级缓存（面试高频）

### 6.1 什么是循环依赖

```java
@Component class A { private final B b; A(B b){this.b=b;} }
@Component class B { private final A a; B(A a){this.a=a;} }
```

启动报错：`BeanCurrentlyInCreationException`——A 创建需要 B，B 创建需要 A，死锁。

### 6.2 三级缓存（Spring 的解法，仅限 singleton）

```text
一级缓存 singletonObjects        成品 Bean
二级缓存 earlySingletonObjects   半成品（已实例化、未完成注入）
三级缓存 singletonFactories      能生产半成品的工厂
```

**流程**（字段/setter 注入时）：

```text
创建 A → 实例化后先把"工厂"放进三级缓存 → A 注入 B 时发现要创建 B
→ 创建 B → B 注入 A 时从三级缓存拿到 A 的半成品 → B 完成 → 放一级缓存
→ 回到 A 继续注入 → A 完成
```

前端类比：ES Module 循环 import 时，JS 也会先给出"占位导出"再填充——同一个思想：**先给半成品引用，事后再补全**。

### 6.3 为什么构造器注入的循环依赖无解

构造器注入要求"创建 A 之前 B 必须就绪"，而三级缓存的解法是"先把半成品 A 给 B"——但半成品 A 还没诞生（构造器没执行完），无物可给。所以：**构造器注入的循环依赖，Spring 直接报错，这是提醒你重新设计**（通常意味着两个类职责纠缠，应抽出第三个类）。

### 6.4 实际建议

- 默认**构造器注入**（不可变、能及早暴露问题）；
- 出现循环依赖报错时，**先想设计是否该拆**，其次才是 `@Lazy` 注入打破环。

---

## 七、动手实验清单（✅ 三实验全部实测 2026-10-21）

| 实验 | 验证什么 | 实测结果 |
|---|---|---|
| ① 生命周期五阶段 | 启动/关闭日志顺序（`BeanLifecycleDemo`） | ✅ **铁证**：①构造器时 `applicationName=null` → ③@PostConstruct 时=`research-agent-backend`（字段注入发生在构造器之后） |
| ② 构造器循环依赖 | 临时加 `CycleA`/`CycleB` 互注 | ✅ 报错原文：`cycleA ← cycleB ← Requested bean is currently in creation: Is there an unresolvable circular reference?`（Spring 还打印了循环链可视化） |
| ③ 多 bean 冲突 | 加第二个 `LlmClient` bean | ⭐ **意外发现（本课最大彩蛋）**：第一次**没报错**！因为 `@Bean` 方法名 `llmClient` 恰好等于注入参数名 `llmClient` → Spring 按"名字匹配"消解了歧义（§五表格"按名匹配"的活案例）。把参数名改成 `client` 后立即报错：`expected single matching bean but found 2: llmClient, httpClientLlmClient`；加 `@Primary` 恢复正常。实验后已恢复原状 |

**实验③的教训**：**多 bean 冲突可以被"名字巧合"掩盖**——生产里加了一个同名 bean 后系统"看起来正常"，实际依赖关系已经变了。排查这类问题用 `getBeanNamesForType(接口.class)` 或启动加 `--debug` 看条件报告。

### 7.1 本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| `backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/BeanLifecycleDemo.java` | 生命周期五阶段日志演示（构造器/@PostConstruct/@PreDestroy） | ✅ 可删（纯教学） |

> 实验②③ 的临时类（`CycleA`/`CycleB`、第二个 `LlmClient` bean）**已全部删除恢复**，不在代码库中。

---

## 八、项目关联地图

| 概念 | 项目位置（代码） | 讲解文档 |
|---|---|---|
| @Bean 配置类 | `LlmClientConfiguration`（场景⑧） | [⑧下游调用治理](../业务场景实战/⑧下游调用治理.md) §三 |
| 集合注入 | `ResearchToolRegistry(List<ResearchTool>)` | 本文档 §二 阶段④ |
| 条件装配 | `LlmClientConfiguration` 按 `clientImpl`/`simulated` 选择 | [⑧下游调用治理](../业务场景实战/⑧下游调用治理.md) §三 |
| 单例无状态 | 所有 Service/Tool（并发安全的根基） | 本文档 §四 |
| @ConfigurationProperties | `LlmProperties`（场景⑧ Q5） | [⑧下游调用治理](../业务场景实战/⑧下游调用治理.md) §十三 |

---

## 九、思考题（自查 + 参考答案）

1. 为什么 `@PostConstruct` 里可以安全用注入的依赖，而构造器里用字段注入的依赖会 NPE？
   **参考答案**：生命周期顺序是"构造器 → 属性注入 → @PostConstruct"。构造器执行时字段注入还没发生（本次实验实测：构造器里 `applicationName=null`）；`@PostConstruct` 在属性注入**之后**，依赖已就绪，可以安全使用。**结论：构造器里只用构造器参数里的依赖，别用字段注入的值。**
2. 为什么我们的 Service 全是 singleton 却线程安全？如果往 Service 里加一个可变实例字段会发生什么？
   **参考答案**：singleton 只保证"一个实例"，**不保证线程安全**——安全来自"**无状态**"（所有字段都是 final 的依赖，没有可变状态）。若加 `private int count`（可变实例字段），多个请求线程共享同一个实例 → 计数错乱/竞态，需要加锁或改为局部变量/`ThreadLocal`。
3. 两个 Bean 用构造器互相注入，Spring 能用三级缓存救活吗？为什么？
   **参考答案**：**不能**。三级缓存的前提是"对象已经实例化完成（构造器执行完），只是依赖没注入"——它能提前暴露"半成品"。构造器注入要求创建 A **之前** B 必须就绪，而 B 又要求 A 就绪，连"半成品 A"都不存在，无物可暴露 → `BeanCurrentlyInCreationException`（本次实验实测）。正确做法是**重新设计**（抽第三个类），把循环依赖当设计信号。
4. `prototype` Bean 的 `@PreDestroy` 会执行吗？为什么？
   **参考答案**：**不会**。容器只负责 prototype bean 的**创建**，不跟踪实例（创建后交给调用方），因此无法在容器关闭时回调销毁。需要清理时：调用方手动调用清理方法，或实现 `DisposableBean` 并自行触发。

---

## 十、现场排障实录（2026-10-21，验证实验①时连环踩三坑）

### 坑 1：启动报 `localhost:5432 refused`，Flyway 连 PostgreSQL——**但 dev 用的是 H2！**

**现象**：加演示 Bean 后启动失败，`FlywayInitializer` 连 `localhost:5432`（PG）被拒。

**排查**：`git diff` yml 无改动 → target/classes 的 dev yml 正常（flyway.enabled=false）→ 但日志有：

```text
[Nacos Config] Load config[dataId=research-agent-backend.yaml, group=DEFAULT_GROUP] success
```

**根因**：**Nacos 远程配置拉取成功了**——`research-agent-backend.yaml` 是当年"数据层进集群"课程留在配置中心的**集群版配置**（PG 数据源 + Flyway）。而 Spring 配置优先级：**远程配置 > 本地 yml**——它把本地 dev 的 H2 覆盖成了 PG（且 `localhost:5432` 在本地机器上根本没有 PG）。

**为什么以前没炸**：以前启动时 Nacos 经常连不上（`Fail to connect server, after trying 13 times`）→ 跳过远程配置 → 用本地 H2。**这次 Nacos 连通了**（虚拟机开着 + 代理的 `nonProxyHosts` 排除了内网 `192.168.*`）→ 远程配置生效 → 炸。

**修复**：本地启动禁用 Nacos 配置中心：

```powershell
-Dspring.cloud.nacos.config.enabled=false   # 已写入 start-dev.ps1
```

**这正是 SP6（配置优先级）的活教材**：远程配置 > 本地 yml > 默认值。

### 坑 2：修复后又报 `ApiExceptionHandler.class cannot be opened`

**根因**：应用运行中 `Stop-Process -Force` 强杀 → `target/classes` 里的 class 文件被截断损坏。

**修复**：`mvn clean compile` 重建。

### 坑 3：再修又报**测试代码编译失败**（4 个错误）

**根因**：场景⑥ 把 `Orchestrator` 的依赖从 `Executor` 换成 `Producer`、`createTask` 返回类型改成 `CreateTaskOutcome`——**主代码改了，测试没跟着改**。`spring-boot:run` 会执行到 test-compile 阶段，所以炸了。

**临时处理**：启动参数加 `-Dmaven.test.skip=true`（跳过测试编译与运行）。

**正式欠账**：→ **场景⑫ 测试体系开课时第一件事就是修它**——而且这本身就是"没有测试网"的最好证据：主代码改了两个月，测试早就编译不过了，却没有任何东西报警。

### 坑 4（连续两轮"杀进程"的教训）

**`Stop-Process -Force` 强杀 Java 时，如果正在编译/写文件，产物会损坏**——先等应用空闲，杀完最好 `clean`。杀进程是钝刀，会留烂摊子。

---

## 十一、本课问答记录（2026-10-21）

（待补——学员提问后按双台账纪律记录）
