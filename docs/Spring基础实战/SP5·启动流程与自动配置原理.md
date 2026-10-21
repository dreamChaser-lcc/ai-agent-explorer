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
| [`AutoConfiguration.imports`](../../backend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 自动配置加载清单（**全项目只有这一行**） | ✅ 可删 |
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

（待补——学员提问后按双台账纪律记录）
