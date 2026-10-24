# SP5 · 启动流程与自动配置原理

> **课程**：Spring 基础实战 · 模块二（2026-10-21）
> **已会**：SP1 的 IoC 容器启动五阶段；场景⑧ 讨论过 starter（langchain4j/nacos 引了就有 bean）
> **本次**：`@SpringBootApplication` 到底做了什么、**自动配置怎么被加载**、`@ConditionalOnXxx` 家族、**手写一个 mini-starter 并验证它真的"自动"**
> **对标**：廖雪峰 23.1 / 23.6 / 23.8 / 23.10
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **SP1 启动五阶段**：准备环境 → 扫描注册 BeanDefinition → 注册 BeanPostProcessor → 实例化 singleton → ready；**`@Component` 只是"登记申请书"，实例化在阶段④**；
- **场景⑧ 的疑问**：pom 里引 `spring-boot-starter-*` 后不用写任何配置类，bean 就自动有了——本课解释这个"魔法"；
- **SP2 代理**：自动配置类本身也可能被代理（`@Configuration` 默认 CGLIB）。

---

## 一、`@SpringBootApplication`：三合一的"启动开关"

```java
@SpringBootApplication
public class ResearchAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResearchAgentApplication.class, args);
    }
}
```

它等价于三个注解叠加：

| 组成注解 | 作用 | 一句话 |
|---|---|---|
| `@SpringBootConfiguration` | 标记这是配置类（`@Configuration` 的特化） | "我是配置" |
| `@EnableAutoConfiguration` | **开启自动配置**——本课主角 | "去把别人的配置也装上" |
| `@ComponentScan` | 扫描**当前包及子包**的 `@Component` | "只扫我家院子" |

**关键边界**：`@ComponentScan` 默认只扫 `com.aiexplorer.researchagent` 及其子包——**包外的类不会被扫到**。这就是为什么第三方库的类必须靠"自动配置"进来（§二 的实验会实测这一点）。

---

## 二、自动配置：Spring Boot 的"发包"机制

### 2.1 手工配置 vs 自动配置

```java
// 没有自动配置时，每个依赖都得自己装配：
@Configuration
public class MyConfig {
    @Bean RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) { ... }
    @Bean KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> factory) { ... }
    // ……每个库都要抄一遍
}
```

自动配置的思路：**库的作者写好自己的默认装配，你"引依赖"就等于"签字同意"**。

### 2.2 加载清单：`AutoConfiguration.imports`

```text
jar 里的固定位置：
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
        ↓ 内容是一行一个类名
com.aiexplorer.starterdemo.GreetingAutoConfiguration
```

Spring Boot 启动时读**所有 jar**（包括自己的 classes 目录）里的这个文件 → 拿到全部自动配置类 → 逐个判断条件 → 满足条件的才注册。

**历史沿革**：2.7 之前用 `META-INF/spring.factories`（一个文件装所有扩展点，容易冲突）；2.7+ 拆成独立文件，**`spring.factories` 已废弃**（只留少量扩展点仍用它）。

### 2.3 为什么要有条件注解

第三方库的自动配置不能"无条件生效"——否则你不想用 Kafka 也会被强制装配。所以每个自动配置类都要"先问条件"：

```java
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)          // classpath 里有这个类才生效
@ConditionalOnProperty("spring.data.redis.host")   // 配了这个属性才生效
public class RedisAutoConfiguration { ... }
```

**一句话**：自动配置 = **清单（imports）+ 条件（@Conditional）**，两者缺一不可。
---

## 三、条件注解家族（最常用的 6 个）

| 注解 | 生效条件 | 项目实例 |
|---|---|---|
| `@ConditionalOnClass` | classpath 里存在指定类 | langchain4j starter：有 `OpenAiChatModel` 才装配 |
| **`@ConditionalOnMissingBean`** | 容器里**没有**同类型 bean | **让用户自定义优先**（★ 本课第三幕验证） |
| `@ConditionalOnProperty` | 配置项等于指定值 | 我们 `LlmClientConfiguration` 的 `simulated` 开关 |
| `@ConditionalOnBean` | 容器里**有**指定 bean | 依赖其他自动配置的产物 |
| `@ConditionalOnWebApplication` | 是 Web 应用 | Web 相关自动配置 |
| `@ConditionalOnExpression` | SpEL 表达式为真 | 复杂条件（少用） |

**优先级铁律**：用户自己定义的 `@Bean` **永远优先于**自动配置——`@ConditionalOnMissingBean` 就是为此而生。

---

## 四、★ 实验：把 starter 放到扫描范围之外

### 4.1 为什么要放出去

如果 starter 写在 `com.aiexplorer.researchagent` 包内，`@ComponentScan` 就能扫到它——**无法区分是"扫描"还是"自动配置"起了作用**。
所以本次实验故意放在**包外**：`com.aiexplorer.starterdemo`。

```text
com.aiexplorer.starterdemo              ← @ComponentScan 扫不到这里！
  ├── GreetingProperties                @ConfigurationProperties(prefix = "app.greeting")
  ├── GreetingService                   普通类（没有 @Component）
  └── GreetingAutoConfiguration         @AutoConfiguration + @ConditionalOnMissingBean

backend/src/main/resources/META-INF/spring/
  └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        └── 内容：com.aiexplorer.starterdemo.GreetingAutoConfiguration
```

### 4.2 三幕验证设计

| 幕 | 操作 | 预期 |
|---|---|---|
| ①默认 | 直接启动 | 日志出现 `[SP5] GreetingService 生效：[auto] Hello, Spring!`——**包在扫描外，只能是 imports 的功劳** |
| ②配置 | 设 `APP_GREETING_PREFIX=【自定义】你好， ` 重启 | 前缀变化 → `@ConfigurationProperties` 绑定生效 |
| ③覆盖 | 应用里自定义一个 `GreetingService` bean | 自动配置退让（不报 bean 冲突）→ `@ConditionalOnMissingBean` 生效 |

---

## 五、starter 的三种写法

| 写法 | 适用场景 | 说明 |
|---|---|---|
| **官方 starter（依赖聚合）** | 引用现成库 | `spring-boot-starter-web` 只是个 pom，靠依赖传递把一堆库拉进来 |
| **auto-configuration（本课）** | 自研组件想"引了就能用" | imports 清单 + 条件装配 + properties 绑定 |
| 纯 jar + `@ComponentScan` | 类都在扫描包内 | 最简单，但没有"可插拔"能力 |

---

## 六、项目关联地图

| 概念 | 项目位置 |
|---|---|
| `@SpringBootApplication` | `ResearchAgentApplication` |
| `@ConditionalOnProperty` | `LlmClientConfiguration`（`simulated` 开关） |
| `@ConfigurationProperties` | `LlmProperties` / `ExecutionProperties` |
| 第三方 starter（自动配置） | langchain4j / nacos / kafka / redis |
| 本课 mini-starter | `com.aiexplorer.starterdemo`（包外，仅靠 imports 加载） |
| 验证入口 | `GreetingDemoRunner`（启动后打印问候语） |

---

## 七、思考题（自查 + 参考答案）

1. 为什么第三方库的类必须靠自动配置进来？<br>
   **参考答案**：`@ComponentScan` 默认只扫**启动类所在包及其子包**；第三方库包名（如 `org.springframework.data.redis`）完全不同，扫不到。自动配置通过 `AutoConfiguration.imports` **主动列出类名**，绕开扫描——本课实验把 starter 放到 `com.aiexplorer.starterdemo`，正是为了证明这一点。
2. `AutoConfiguration.imports` 和 `spring.factories` 是什么关系？<br>
   **参考答案**：2.7 之前自动配置写在 `META-INF/spring.factories`（与监听器等扩展点混在一个文件）；2.7+ 拆到独立的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，一行一个类名。旧写法仍兼容但已废弃，新项目用 imports。
3. `@ConditionalOnMissingBean` 解决什么问题？<br>
   **参考答案**：让**用户自定义优先**。库作者提供默认实现，但你一旦自己定义了同类型 bean，自动配置就自动退让——"约定优于配置，但允许覆盖"。典型：`RedisTemplate`、`ObjectMapper`、`RestTemplate` 都遵循这个模式。
4. 自动配置类能不能被 `@ComponentScan` 扫到？<br>
   **参考答案**：能（如果它在扫描包内），但**不推荐依赖扫描**：第三方库天然在包外，只有 imports 能加载它；依赖扫描会让"能不能生效"取决于包结构，失去可插拔性。

---

## 八、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| [`GreetingProperties.java`](../../backend/src/main/java/com/aiexplorer/starterdemo/GreetingProperties.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/starterdemo/GreetingProperties.java) | 配置属性（默认值） | ✅ 可删 |
| [`GreetingService.java`](../../backend/src/main/java/com/aiexplorer/starterdemo/GreetingService.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/starterdemo/GreetingService.java) | 被自动装配的服务（无 @Component） | ✅ 可删 |
| [`GreetingAutoConfiguration.java`](../../backend/src/main/java/com/aiexplorer/starterdemo/GreetingAutoConfiguration.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/starterdemo/GreetingAutoConfiguration.java) | 自动配置类（条件装配） | ✅ 可删 |
| [`AutoConfiguration.imports`](../../backend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 自动配置加载清单（**全项目只有这一行**） | ✅ 可删 |
| [`GreetingDemoRunner.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/starterdemo/GreetingDemoRunner.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/starterdemo/GreetingDemoRunner.java) | 启动后打印验证 | ✅ 可删 |

> 成组删除：删掉 imports 文件后 starter 立即失效（这正是"清单驱动"的实证）。

---

## 九、验证记录（2026-10-21 三幕全绿）

| 幕 | 操作 | 实测日志 | 判定 |
|---|---|---|---|
| **① 默认** | 无任何配置启动 | `[SP5] GreetingService 生效：[auto] Hello, Spring!（来源=auto-configuration）` | ✅ **包在扫描范围外，靠 imports 清单加载成功** |
| **② 配置** | `APP_GREETING_PREFIX="[custom] Hi, "` 重启 | `[SP5] GreetingService 生效：[custom] Hi, Spring!（来源=auto-configuration）` | ✅ `@ConfigurationProperties` 绑定生效 |
| **③ 覆盖** | 应用内自定义 `GreetingService` bean | `[SP5] GreetingService 生效：[user-bean] Hello, Spring（来源=user-defined）`，**无 bean 冲突异常** | ✅ `@ConditionalOnMissingBean` 自动退让 |

**三幕合起来证明了一件事**：

```text
starter 能被加载  →  功劳在 AutoConfiguration.imports（不是 @ComponentScan，包在外头）
能被配置         →  功劳在 @ConfigurationProperties + 环境变量
能被覆盖         →  功劳在 @ConditionalOnMissingBean（用户自定义优先）
```

### 9.1 过程踩坑：clean 之后才能启动（第二次踩）

第二幕首次启动失败，日志特征：

```text
Flyway 跑 V1__init_research_agent_schema.sql（dev 本应禁用）
日志无 [research-agent-backend] 应用名标签
```

排查结论：**强杀 JVM 后 `target/classes` 编译产物不完整**（配置文件/类文件缺失），导致 `spring.application.name`、profile 配置都读不到。`mvn clean compile` 后立即恢复正常。

> **教训**：`Stop-Process -Force` 杀 Java 后，启动前先 `clean`（今天第二次踩，SP1 也遇到过）。

### 9.2 可补做的验证

- 临时删掉 `META-INF/spring/...AutoConfiguration.imports` → 重启 → 日志应变成 `[SP5] GreetingService 不存在 → 自动配置未生效`，证明"清单驱动"。

---

## 十、本课问答记录（2026-10-21）

| # | 提问（原话） | 落档位置 |
|---|---|---|
| **Q1** | "自动装配我不是很理解，复习下，手工配置和自动配置是怎么样区别我没看懂" | §10.1「手工配置 vs 自动配置：谁写 @Bean + 怎么被加载」（见下，含 jar/javap 实证） |
| **Q2** | "@ComponentScan 是需要我们写的吗，还是 spring 去写的" | §10.2「@ComponentScan：Spring Boot 已经写好了」（见下） |
| **Q3** | "@Configuration 和 @Bean 单独出现吗，还是一定要成对出现才行" | §10.3「@Configuration 与 @Bean：可以不成对，但 @Bean 要住在配置类里」（见下） |
| **Q4** | "CGLIB 代理是什么" | §10.4「CGLIB：运行时造子类」（见下，含 8 个配置类的 type 实测；原理详见 [SP2](SP2·AOP与动态代理.md)） |

### 10.1 手工配置 vs 自动配置（Q1 · 2026-10-24 复习）

#### 一、三个硬证据（实测）

```text
① jar 里确实有这个类：
   spring-boot-autoconfigure-3.5.13.jar
     └── org/springframework/boot/autoconfigure/data/redis/RedisAutoConfiguration.class

② 清单确实登记了它（AutoConfiguration.imports 共 156 行）：
   org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration

③ javap 反编译出它的方法：
   public RedisTemplate<Object,Object> redisTemplate(RedisConnectionFactory);
   public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory);   ← ★
```

**场景⑦⑨ 用的 `StringRedisTemplate` 就是第 ③ 行的产物——不是我们写的。**

#### 二、手工配置（你写的）

```java
// src/main/java/.../MyRedisConfig.java
@Configuration                                   // ← 你写
public class MyRedisConfig {
    @Bean                                        // ← 你写
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

加载方式：`@ComponentScan` 扫到。

#### 三、自动配置（官方写的，内容一模一样）

```java
// spring-boot-autoconfigure-3.5.13.jar 里
@AutoConfiguration                                // ← 官方写
@ConditionalOnClass(RedisOperations.class)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);  // 和你写的一模一样
    }
}
```

加载方式：`AutoConfiguration.imports` 清单（156 行）。

#### 四、核心差别只有两点

| 维度 | 手工配置 | 自动配置 |
|---|---|---|
| **@Bean 谁写** | **你** | **官方/库作者** |
| **怎么被加载** | `@ComponentScan` | `AutoConfiguration.imports` 清单 |

**其余完全相同**（`@Configuration` + `@Bean` + 构造器注入），没有任何魔法。

#### 五、"自动"的三步

```text
① 引依赖 → 传递引入 spring-boot-autoconfigure.jar（156 个自动配置类）
② 启动读清单 → 逐个判断条件（@ConditionalOnClass / @ConditionalOnMissingBean）
③ 满足条件 → 注册 bean → 你能 @Autowired
```

#### 六、四种组合（关键）

| 你的 @Bean | jar 里的 | 结果 |
|---|---|---|
| ❌ | ✅ | **自动配置生效**（StringRedisTemplate） |
| ✅ | ✅ | **你的赢**（@ConditionalOnMissingBean 退让，实验③验证） |
| ✅ | ❌ | 你的生效（LlmClient） |
| ❌ | ❌ | 注入失败（NoSuchBeanDefinition） |

#### 七、项目三个实例

| bean | 谁写 @Bean | 加载方式 |
|---|---|---|
| `StringRedisTemplate` | Spring Boot 官方 | imports 清单 |
| `LlmClient` | 我们（`LlmClientConfiguration`） | `@ComponentScan` |
| `GreetingService` | 我们（mini-starter，包外） | imports 清单（**模拟官方**） |

#### 八、三个误解

| 误解 | 真相 |
|---|---|
| 自动配置是运行时生成代码 | ❌ 类早编译好在 jar 里（javap 反编译可见字节码） |
| 自动配置和手工配置是两套机制 | ❌ 同一套，差别只在"谁写 + 怎么扫描" |
| 引了依赖就有 bean | ❌ 要过条件（SP10 实验：chatModel/nacosConfigManager 都没进容器） |

#### 九、一句话

```text
手工配置：你写 @Bean，@ComponentScan 扫到
自动配置：官方写 @Bean，imports 清单加载，满足条件才生效
"自动" = 别人替你把 @Bean 写好了
```

### 10.2 @ComponentScan 是谁写的（Q2 · 2026-10-24）

#### 一、答案：Spring Boot 已经替你写好

```java
@SpringBootApplication     // 你只写这一个
public class ResearchAgentApplication { ... }
```

它是三合一：

```java
@SpringBootConfiguration           // 等价 @Configuration
@EnableAutoConfiguration           // 开自动配置
@ComponentScan                     // ★ 已包含
```

**项目实测**：全项目 `@ComponentScan` **零处真实使用**（只有注释提到）。

#### 二、默认扫描范围 = 启动类所在包及子包

```text
com.aiexplorer.researchagent        ← 启动类在这（扫描根）
   ├── api/ application/ infrastructure/ shared/    ✅ 扫
com.aiexplorer.starterdemo          ← 兄弟包，❌ 扫不到
```

**这正是 SP5 实验的设计**：starter 放兄弟包，证明它只能靠 imports 清单加载。

#### 三、什么时候需要显式写

| 场景 | 写法 |
|---|---|
| 启动类不在顶层包 | `@ComponentScan(basePackages = {"包A", "包B"})` |
| 扫描第三方包（不推荐，应让对方提供 imports） | 同上 |
| 排除某些包 | `@ComponentScan(excludeFilters = ...)` |

⚠️ **显式写会完全覆盖默认范围**（不是追加）——必须把原包也写进去。

#### 四、@ComponentScan 做了什么

```text
① 解析到 @ComponentScan → 拿 basePackages（默认启动类所在包）
② 扫描 classpath，找 @Component/@Service/@Repository/@Controller/@Configuration
③ 每类注册 BeanDefinition（图纸）
④ 交给 SP1 的实例化阶段创建对象
```

**只登记图纸，不创建实例**——"扫到了" ≠ "bean 能用"（还要过条件、依赖满足）。

#### 五、三个入口（不互斥）

| 方式 | 谁写 | 适用 |
|---|---|---|
| `@ComponentScan`（默认） | Spring Boot 组合好了 | 自己的代码（99%） |
| `@Bean`（手工配置） | 你 | 第三方对象、需自定义 |
| `AutoConfiguration.imports` | 库作者 | 第三方 starter |

### 10.3 @Configuration 与 @Bean 可以不成对（Q3 · 2026-10-24）

> **本节是结论速览**；**每种组合到底用来干什么**（三类注解语义 / 四种组合的用途与项目实例 / 决策树 / 项目全景）→ 见 **§10.5**。

#### 一、项目实测

```text
@Configuration 15 处（含 @ConfigurationProperties 误匹配，真实约 10 个）
@Bean 8 处
→ 数量不对等，说明不成对
```

| 组合 | 项目实例 | 说明 |
|---|---|---|
| @Configuration ✅ + @Bean ✅ | `LlmClientConfiguration` | 经典配置类 |
| **@Configuration ✅ + @Bean ❌** | `WebCorsConfiguration` / `CleanupConfiguration` / `Sp8InterceptorConfiguration` / `WebRequestTraceConfiguration` / `Sp6DemoConfiguration` | **合法且常见** |
| @Bean ✅ + @Configuration ❌ | **0 处** | 可行但有坑 |
| 都 ❌ | 普通类 | — |

#### 二、@Configuration 单独出现的三种用途

| 用途 | 实例 |
|---|---|
| 实现 WebMvcConfigurer | `WebCorsConfiguration`、`Sp8InterceptorConfiguration` |
| 开功能开关 | `CleanupConfiguration`（`@EnableScheduling`） |
| 只做属性绑定 | `Sp6DemoConfiguration`（`@EnableConfigurationProperties`） |

#### 三、@Bean 不放 @Configuration 的坑（lite mode）

```java
@Component                                 // 代替 @Configuration
public class MyConfig {
    @Bean public A a() { return new A(); }
    @Bean public B b() { return new B(a()); }    // 调 a()
}
```

```text
无 CGLIB 代理 → b() 里的 a() 是普通方法调用 → new 出第 2 个 A
容器里 1 个 A，B 却持有另 1 个 A → 两个实例共存（隐蔽 bug）
```

`@Configuration` 默认 `proxyBeanMethods = true` → CGLIB 拦截 `a()` → 返回同一单例。

#### 四、full mode vs lite mode

| | full（@Configuration 默认） | lite（@Component/无注解） |
|---|---|---|
| CGLIB 代理 | ✅ | ❌ |
| @Bean 互相调用 | 同一实例 | 每次 new |
| 启动开销 | 稍慢 | 快 |
| 前提 | 无 | @Bean 禁止互相调用 |

#### 五、官方做法

```java
@Configuration(proxyBeanMethods = false)   // Boot 自动配置类全都这么写
```

前提：@Bean 方法互不调用；好处：不生成 CGLIB 类，启动更快。
**本项目零处显式使用**（全默认 true）——安全，可优化。

#### 六、实践建议

```text
✅ @Configuration 单独出现：合法（实现接口/开开关/绑属性）
✅ @Bean 一律放 @Configuration（项目 0 例外）
❌ 别用 @Component 装 @Bean（lite mode 的"两个 A"陷阱）
⚙️ @Bean 互不调用时可加 proxyBeanMethods = false 提速
```

### 10.4 CGLIB 代理是什么（Q4 · 2026-10-24）

> 完整原理见 [SP2 · AOP 与动态代理](SP2·AOP与动态代理.md)；本节聚焦"在 @Configuration 场景下它做了什么"。

#### 一、一句话定义

```text
CGLIB = Code Generation Library
      = 运行时用字节码生成一个类的【子类】，覆写其方法并插入增强逻辑
不要求接口，只要求"类/方法不能是 final"
```

#### 二、为什么需要（JDK 代理的局限）

JDK 动态代理只能代理**接口**（`Proxy.newProxyInstance` + `InvocationHandler`）；现代项目流行"不写接口、直接注入实现类" → JDK 代理无能为力。

CGLIB 用**子类继承**解决：

```java
public class UserServiceImpl$$EnhancerBySpringCGLIB$$1a2b3c extends UserServiceImpl {
    @Override public void save() {
        // 前置增强
        super.save();      // 调真身
        // 后置增强
    }
}
```

#### 三、在 @Configuration 里增强什么

```java
@Configuration                    // proxyBeanMethods = true（默认）
public class AppConfig {
    @Bean public A a() { return new A(); }
    @Bean public B b() { return new B(a()); }   // 调 a()
}
```

```text
① CGLIB 生成 AppConfig$$SpringCGLIB$$0 extends AppConfig
② 子类覆写 a()：容器已有 → 返回容器里的；没有 → super.a() 创建
③ 容器里放的是【子类实例】→ b() 里调 a() 拿到同一个单例
```

关闭（`proxyBeanMethods = false`）：无子类 → `b()` 里 `a()` 是普通调用 → **new 出第 2 个 A**。

#### 四、实测（2026-10-24，/actuator/beans）

```text
cacheConfiguration           → CacheConfiguration$$SpringCGLIB$$0
llmClientConfiguration       → LlmClientConfiguration$$SpringCGLIB$$0
cleanupConfiguration         → CleanupConfiguration$$SpringCGLIB$$0
webCorsConfiguration         → WebCorsConfiguration$$SpringCGLIB$$0
webRequestTraceConfiguration → WebRequestTraceConfiguration$$SpringCGLIB$$0
asyncExecutionConfiguration  → AsyncExecutionConfiguration$$SpringCGLIB$$0
kafkaTopicConfiguration      → KafkaTopicConfiguration$$SpringCGLIB$$0
propertiesConfiguration      → PropertiesConfiguration$$SpringCGLIB$$0

redisCacheConfiguration      → org.springframework.data.redis.cache.RedisCacheConfiguration（无后缀）
```

**关键区别**：
- **@Configuration 类本身** → 被 CGLIB 增强（要拦 `@Bean` 方法）
- **@Bean 产出的对象** → 原样（不是配置类增强）

#### 五、同一个 CGLIB，两种用途

| 用途 | 场景 | 拦什么 | 目的 |
|---|---|---|---|
| AOP 代理 | `@Aspect`（场景⑤） | 业务方法 | 日志/事务/缓存 |
| @Configuration 增强 | `proxyBeanMethods=true` | `@Bean` 方法 | 保证单例 |

#### 六、代价与限制

| 限制 | 原因 | 现象 |
|---|---|---|
| final 类不能代理 | 不能继承 | 报错/静默失效 |
| final 方法不能增强 | 不能覆写 | 增强丢失 |
| private 方法不能增强 | 子类访问不到 | 静默失效 |
| 启动变慢 | 生成字节码 | 配置类多时明显 |
| Metaspace 占用 | 每个代理类一个 Class | 大量代理时注意 |

**Boot 3 官方自动配置全用 `proxyBeanMethods = false`** —— 不生成 CGLIB 类，启动更快。

#### 七、JDK vs CGLIB

| | JDK 动态代理 | CGLIB |
|---|---|---|
| 原理 | 接口的实现类 | 目标类的**子类** |
| 前提 | 必须有接口 | 类/方法不能 final |
| 创建/调用成本 | 创建快、调用稍慢（反射） | 创建慢、调用快（虚分派） |
| Boot 默认 | — | ✅ `proxyTargetClass=true` |

#### 八、一句话

```text
CGLIB = 运行时造子类、覆写方法、插增强
  @Configuration 里：拦 @Bean 调用 → 保证单例
  AOP 里：拦业务方法 → 插日志/事务/缓存
```

### 10.5 四种组合的使用场景详解（Q3 补充 · 2026-10-24）

> §10.3 讲的是"能不能不成对 + lite mode 坑"；本节讲**每种组合到底用来干什么**。

#### 零、三类注解的语义（先记这个）

| 注解 | 一句话语义 | 产出 |
|---|---|---|
| `@Component` 家族（`@Service`/`@Repository`/`@Controller`） | **"我本身就是一个 bean"** | 1 个 bean（自己） |
| `@Configuration` | **"我是配置类，负责创建 bean 或修改框架规则"** | 0~N 个 bean + 框架行为 |
| `@Bean` | **"我所在方法返回的结果是一个 bean"** | 1 个 bean（方法的返回值） |

**核心区分**：
- `@Component`：我被注入到别处**使用**
- `@Configuration`：我给别人**创建**对象，或**修改**框架规则

#### 一、组合 1：@Configuration ✅ + @Bean ✅ —— "我来生产 bean"

##### 什么时候必须用（三个场景）

| 场景 | 为什么不能用 @Component |
|---|---|
| **① 要创建的是第三方类** | `OpenAiChatModel`/`NewTopic`/`RedisCacheConfiguration` 在 jar 里，**不能给它加注解** |
| **② 对象的创建过程很复杂** | 要读配置、选实现、包 Guard、传多个依赖 |
| **③ 需要条件装配** | 同类型两个实现按配置选一个 |

##### 项目的 4 个实例

**① `LlmClientConfiguration`（3 个 @Bean）**

```java
@Configuration
public class LlmClientConfiguration {
    @Bean public LlmClient llmClient(LlmProperties props, ObjectMapper mapper) { ... }
    @Bean public LlmContentGenerator llmContentGenerator(LlmCallGuard guard, ObjectMapper mapper) { ... }
    @Bean ...
}
```

在干什么：`LlmClient` 的构造需要"读配置 → 选实现（真实/模拟）→ 包超时重试 → 造 ChatModel"四步，且要条件装配（`@ConditionalOnProperty`）。

**② `KafkaTopicConfiguration`**

```java
@Configuration
public class KafkaTopicConfiguration {
    @Bean
    public NewTopic researchTaskExecutionTopic() { return TopicBuilder.name("...").build(); }
}
```

在干什么：`NewTopic` 是 **Kafka 库的类**（第三方），bean 的作用是"启动时自动建 topic"。

**③ `CacheConfiguration`**

```java
@Configuration
public class CacheConfiguration {
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() { ... 序列化器 + TTL ... }
}
```

在干什么：构造 `RedisCacheConfiguration` 需要设一堆参数（JSON 序列化、30 分钟 TTL、禁 null）——**是一段逻辑，不是一个类**。

**④ `AsyncExecutionConfiguration`**

```java
@Configuration
public class AsyncExecutionConfiguration {
    @Bean(name = "researchTaskExecutor")
    public Executor researchTaskExecutor(ExecutionProperties props) { ... 线程池参数 ... }
}
```

在干什么：`Executor` 是 **JDK 接口**，实现类 `ThreadPoolExecutor` 要 7 个参数——必须"先造后注入"。

##### 一句话总结

```text
"我手里有个对象要造，但它要么是别人的类，要么造它需要一堆步骤" → 写 @Bean
```

#### 二、组合 2：@Configuration 单独 —— "我来改框架行为 / 开开关"

**注意：这类配置类不产生 bean**，但它本身是 bean（会被注册），价值在于**副作用**。

##### 2a. 实现扩展接口（改框架行为）

```java
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*");   // 改跨域规则
    }
}
```

为什么不写 @Bean：
- `WebMvcConfigurer` 是 Spring MVC 的**扩展点接口**
- 启动时 Spring **收集容器里所有实现**，逐个调用 `addXxx`
- **设计意图是"注册一套规则"，不是"创建对象"**

**项目另一个例子**（SP8 加的）：

```java
@Configuration
public class Sp8InterceptorConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**");
    }
}
```

关键区分：
- `PermissionInterceptor` 本体是 `@Component`（**它是 bean**）
- **把拦截器注册进 MVC**必须靠配置类（**注册不是创建**）

##### 2b. 开功能开关（@EnableXxx）

```java
@Configuration
@EnableScheduling                                          // 开定时任务
@EnableConfigurationProperties(CleanupProperties.class)    // 开属性绑定
public class CleanupConfiguration { }
```

为什么不写 @Bean：
- `@EnableScheduling` 的作用是**注册一批基础设施 bean**（`ScheduledAnnotationBeanPostProcessor`），Spring 替你做
- 你的配置类只需**挂上开关**，不需要自己造任何东西

##### 2c. 只声明"我接受这些配置"（属性绑定）

```java
@Configuration
@EnableConfigurationProperties({LlmProperties.class, ExecutionProperties.class, ...})
public class PropertiesConfiguration { }
```

作用：让 `@ConfigurationProperties` 类被识别并绑定（否则那些 Properties 类只是普通类）。

##### 一句话总结

```text
"我不造对象，我只是要【改规则】（CORS/拦截器/序列化）或【开开关】（定时/缓存/属性绑定）"
→ 删掉 @Bean，保留 @Configuration（开关注解必须挂在配置类上）
```

#### 三、组合 3：@Bean ✅ + @Configuration ❌ —— "应该避免"

**项目 0 处**——不是"另一种选择"，是**坑**。

```java
@Component                                  // ❌ 换掉了 @Configuration
public class MyConfig {
    @Bean public A a() { return new A(); }
    @Bean public B b() { return new B(a()); }    // 调 a()
}
```

后果：
```text
无 CGLIB 代理 → b() 里调 a() 是普通方法调用
→ 第 2 个 A 被 new 出来（不在容器里）
→ 容器 1 个 A，B 却持有另 1 个 A
```

什么情况下"勉强可以"：类里只有一个 `@Bean` 且不被其他 `@Bean` 调用。但**没有理由冒险**——改成 `@Configuration` 零成本。

#### 四、组合 4：两个都没有 —— "我自己就是 bean"

```java
@Service  public class ResearchTaskOrchestrator { ... }
@Repository public interface ResearchTaskRepository { ... }
@RestController public class ResearchTaskController { ... }
```

为什么不需要 @Configuration：

```text
它们的职责是"被使用"，不是"生产别人"
→ @Service/@Repository/@RestController 已声明"我是个 bean"
→ 它们不创建任何对象，不需要 @Bean
```

容易混淆的点：

```java
@Service
public class A {
    public A(B b) { }          // 需要 B → 别人（@Configuration）创建了 B
}

@Configuration
public class C {
    @Bean public B b() { ... } // 我创建 B
}
```

**`A` 需要 `B`，但 `A` 不制造 `B`**——制造 `B` 是配置类的职责。

#### 五、决策树

```text
写这个类之前，先问：它是干什么的？
│
├─ ① "我是业务对象，要被注入到别处用"
│      → @Service / @Component / @Repository / @Controller（组合 4）
│
├─ ② "我要造一个对象给别人用"
│      → 而且它是"第三方类 / 造它要几步 / 要条件装配"
│      → @Configuration + @Bean（组合 1）
│
├─ ③ "我要改框架规则（CORS/拦截器/Jackson）或开开关（@EnableXxx）"
│      → @Configuration，不写 @Bean（组合 2）
│
└─ ④ "我想省一个注解，用 @Component 装 @Bean"
       → ❌ 别这么干（组合 3，lite mode 坑）
```

#### 六、项目全景对照

| 文件 | 组合 | 具体在干什么 |
|---|---|---|
| `LlmClientConfiguration` | 1 | 创建 `LlmClient`/`LlmContentGenerator`（条件装配真实/模拟） |
| `KafkaTopicConfiguration` | 1 | 创建 `NewTopic`（第三方类，启动建 topic） |
| `CacheConfiguration` | 1 | 创建 `RedisCacheConfiguration`（序列化器 + TTL） |
| `AsyncExecutionConfiguration` | 1 | 创建线程池 `Executor`（JDK 接口，7 个参数） |
| `WebCorsConfiguration` | 2a | 实现 `addCorsMappings`（跨域规则） |
| `Sp8InterceptorConfiguration` | 2a | 实现 `addInterceptors`（注册权限拦截器） |
| `CleanupConfiguration` | 2b | `@EnableScheduling`（开定时任务） |
| `PropertiesConfiguration` | 2b | `@EnableConfigurationProperties`（开属性绑定） |
| `Sp6DemoConfiguration` | 2b | 同上（SP6 实验） |
| **（0 处）** | 3 | 应避免 |
| `ResearchTaskOrchestrator` 等 | 4 | 业务 Service / Controller / Repository |

#### 七、三句话钉死区别

```text
① @Component = "我是 bean"（等着被用）
② @Configuration = "我是配置类"（造 bean 或改规则）
③ @Bean = "这个方法的结果是 bean"（必须住在 @Configuration 里）

造对象 → 组合 1；改规则/开开关 → 组合 2；自己被用 → 组合 4；组合 3 别碰
```
