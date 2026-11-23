# SB1 · 自动配置原理深入（@Conditional 家族与装配链）

> **一课目标**：看懂 Spring Boot 的"自动配置"到底怎么装配的——`@SpringBootApplication` 一个注解背后发生了什么、
> `@Conditional` 家族怎么决定"装不装"、以及**怎么在项目里查**（`--debug` / `/actuator/conditions`）。
> **前置**：[SP5·启动流程与自动配置原理](../Spring基础实战/SP5·启动流程与自动配置原理.md)（讲"发生了什么"）——本课讲"怎么读、怎么扩展"。
> **后续**：SB2 自定义 Starter（手写一个）

---

## 一、装配链：一个注解背后的四步

```text
@SpringBootApplication
 ├── @SpringBootConfiguration        （= @Configuration，标记配置类）
 ├── @ComponentScan                  （扫当前包及子包：@Component/@Service/@Controller）
 └── @EnableAutoConfiguration        ★ 自动配置的入口
      └── @Import(AutoConfigurationImportSelector.class)
            ↓ 启动时执行
      ┌──────────────────────────────────────────────────────────────┐
      │ ① 读取候选清单：                                               │
      │    META-INF/spring/org.springframework.boot.autoconfigure     │
      │        .AutoConfiguration.imports                              │
      │    （Boot 2.7+ 的新位置；更早版本是 spring.factories 的        │
      │      EnableAutoConfiguration 键）                              │
      │    → 约 150 个自动配置类（Redis / Kafka / DataSource / Web…）   │
      │                                                               │
      │ ② 去重 + 排序（@AutoConfigureOrder / Before / After）            │
      │                                                               │
      │ ③ 条件过滤（@Conditional 家族）：不满足的整类跳过 ★ 核心       │
      │                                                               │
      │ ④ 注册命中的配置类的 Bean（@Bean 方法）                         │
      └──────────────────────────────────────────────────────────────┘
```

**对应项目（pom 里的依赖 → 自动配置类）**：

```text
spring-boot-starter-web        → WebMvcAutoConfiguration / JacksonAutoConfiguration
spring-boot-starter-data-jpa   → DataSourceAutoConfiguration / JpaRepositoriesAutoConfiguration
spring-boot-starter-data-redis → RedisAutoConfiguration / RedisRepositoriesAutoConfiguration
spring-kafka                   → KafkaAutoConfiguration
spring-boot-starter-actuator   → 各 EndpointAutoConfiguration
```

## 二、@Conditional 家族（决定"装不装"的六把尺子）

| 注解 | 判断条件 | 项目实例 |
|---|---|---|
| `@ConditionalOnClass` | **classpath 上有某类**才装配 | `RedisAutoConfiguration` 要求 `RedisOperations` 存在；没引 redis starter 就整个跳过 |
| `@ConditionalOnMissingBean` | **容器里没有该 Bean** 才装配（留给用户覆盖） | 你自定义 `CacheManager` 后，`CacheAutoConfiguration` 的默认实现自动让位 |
| `@ConditionalOnProperty` | 配置项等于某值才装配 | ★ 项目的 `CacheConfiguration`（`app.cache.enabled=true`，`matchIfMissing=true`）；`KafkaErrorHandlingConfig`（无开关，默认装） |
| `@ConditionalOnBean` | 容器里**有**某 Bean 才装配 | `RedissonTaskLock`（`@ConditionalOnBean(RedissonClient.class)`：没有 Redisson 就不建，lite 模式自动降级） |
| `@ConditionalOnWebApplication` | Web 环境才装配 | `DispatcherServletAutoConfiguration`（非 Web 应用跳过） |
| `@ConditionalOnResource` / `@ConditionalOnExpression` | 资源存在 / SpEL 表达式 | 少用（如 `@ConditionalOnExpression("...")` 做复杂开关） |

```text
★ 组合用法（自动配置类的常规套路）：
   @AutoConfiguration
   @ConditionalOnClass(RedisOperations.class)          ← 依赖在
   @ConditionalOnProperty(name="spring.data.redis.host") ← 配置了就装
   @EnableConfigurationProperties(MyProps.class)         ← 绑定属性
   public class MyAutoConfiguration { @Bean @ConditionalOnMissingBean ... }
```

## 三、怎么"读"自动配置（三种方法，按场景）

### 方法 1：启动加 `--debug`（离线看，最全）

```powershell
# 项目里（lite 模式最快，无外部依赖）
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=lite" "-Dspring-boot.run.arguments=--debug"
```

日志里会出现 **CONDITIONS EVALUATION REPORT**：

```text
============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:                    ← 命中（真正装配了）
-----------------
   RedisAutoConfiguration matched:   ← 但有条件子项
      - @ConditionalOnClass found required class 'org.springframework.data.redis.core.RedisOperations'

Negative matches:                    ← 未命中（被条件跳过）
-----------------
   RedisAutoConfiguration.RedisRepositoriesAutoConfiguration did not match
      - @ConditionalOnMissingBean (types: ...RedisRepositoryFactoryBean...) found beans
   
   KafkaAutoConfiguration matched:
      - @ConditionalOnClass found required class 'org.springframework.kafka.core.KafkaTemplate'

Exclusions:
-----------
   （无）
```

**读法**：

```text
① 先看 Positive matches：谁装配了、因为哪个条件命中
② 再看 Negative matches：谁没装配、卡在哪个条件（★ 排障关键）
③ Exclusions：被 spring.autoconfigure.exclude 排除的（项目 lite 里排除了 RedissonAutoConfigurationV2）
```

### 方法 2：`/actuator/conditions`（运行时看）

```powershell
# 需要在 application.yml 暴露端点（项目 SP7 讲过）
management.endpoints.web.exposure.include: health,info,conditions
# 访问
curl http://localhost:8080/actuator/conditions
```

```text
优点：不用重启（改配置后实时看）
缺点：JSON 输出要自己找（比日志难读）
```

### 方法 3：直接读源码（最准，理解实现）

```text
在 spring-boot-autoconfigure-3.5.13.jar 里：
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← 候选清单
  org/springframework/boot/autoconfigure/**/XxxAutoConfiguration.java             ← 每个配置类
项目里也可以在 target/dependency 或 ~/.m2 里找到这个 jar
```

## 三点五、怎么知道 Boot 有没有给我默认实现（4 种查法，2026-11-23 学员问）

> "默认给你一个能用的"——怎么知道它给了？答案是现场查，不是背。

### 查法 1：--debug 的 condition report（最快）

```text
看 Positive matches 里的「XxxAutoConfiguration#bean名 matched」：

  KafkaAutoConfiguration#kafkaTemplate matched        ← 它给了 KafkaTemplate
  KafkaAutoConfiguration#kafkaAdmin matched           ← 它给了 KafkaAdmin
  RedisAutoConfiguration#redisTemplate matched        ← 它给了 RedisTemplate
  RedisAutoConfiguration#stringRedisTemplate matched  ← 它给了 StringRedisTemplate
  SimpleCacheConfiguration matched                    ← 它给了内存缓存实现

★ 格式规律：AutoConfiguration#方法名 matched = 这个 @Bean 方法被装配了（它就是"给你的默认实现"）
```

### 查法 2：/actuator/beans（最准——看容器里真有什么）

```yaml
management.endpoints.web.exposure.include: health,info,beans
```

```powershell
curl.exe -s http://localhost:8080/actuator/beans | Select-String "cacheManager|redisTemplate|kafkaTemplate"
# → "cacheManager": { "type": "org.springframework.cache.concurrent.ConcurrentMapCacheManager" }
#   连实现类名都给你看——这才是"确定性最高"的答案
```

```text
★ report 说"配置类命中了"（可能给 Bean 也可能只给配置）；beans 说"容器里真有这个 Bean"
```

### 查法 3：看 AutoConfiguration 源码（权威清单，不依赖运行）

```text
spring-boot-autoconfigure-3.5.13.jar 里：
  org/springframework/boot/autoconfigure/cache/CacheAutoConfiguration.java
    → CacheConfigurationImportSelector 按 spring.cache.type 选实现
    → 每个 @Bean 方法上的 @ConditionalOnMissingBean = "默认给了什么、什么时候让位"
  同类：KafkaAutoConfiguration（kafkaTemplate/kafkaAdmin/consumerFactory...）
        RedisAutoConfiguration（redisTemplate/stringRedisTemplate/connectionFactory）
```

### 查法 4：官方文档附录（一页看全）

```text
Spring Boot Reference → "Auto-configuration classes"
https://docs.spring.io/spring-boot/reference/using/auto-configuration.html
→ 每个版本的 ~150 个自动配置类清单（哪个 starter 带来哪些配置类）
```

### 终极验证法（推荐——反证最可靠）

```text
怀疑"它给没给 X"？自己定义一个 X 的 Bean → 再 --debug：
  · 原来 Positive 的 XxxAutoConfiguration 变 Negative（@ConditionalOnMissingBean 不成立）
  → 反证：它本来就在给你默认值（否则不会因为你的定义而退让）

项目实例：项目定义 redisCacheConfiguration(@Bean) 后，
         CacheAutoConfiguration 的默认 CacheManager 让位。
```

### 心理模型（三句话）

```text
① "Boot 给了什么"不是固定答案——取决于【依赖 + 配置 + 条件】三者
② "怎么知道" = 现场查：--debug（配置类命中）→ /actuator/beans（真实 Bean）
③ "不确定就反证"：自定义一个同类型 Bean → 看 report 变化
```
## 四、项目实战：三个"自动配置/条件装配"的真实案例

### 案例 1：`app.cache.enabled` 控制缓存配置（@ConditionalOnProperty）

```java
// 项目代码：infrastructure/config/CacheConfiguration.java
@Configuration
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfiguration {
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() { ... }
}
```

```text
lite 模式（application-lite.yml: app.cache.enabled=false）
  → 条件不满足 → 整个 CacheConfiguration 跳过 → 不建 RedisCacheConfiguration
  → Spring Boot 的 CacheAutoConfiguration 用默认 SimpleCacheManager（内存缓存）
→ 这就是"@ConditionalOnProperty 一处开关，行为优雅降级"的实例

→ SB1 之后你应该能回答：为什么 lite 下 @Cacheable 不报错？（缓存实现自动降级为内存）
```

### 案例 2：`RedissonTaskLock` 的自动降级（@ConditionalOnBean）

```java
// 项目代码：infrastructure/lock/RedissonTaskLock.java
@Component
@ConditionalOnBean(RedissonClient.class)
public class RedissonTaskLock implements ... { ... }
```

```text
lite 模式：application-lite.yml 里 spring.autoconfigure.exclude 排除了
           RedissonAutoConfigurationV2 → 没有 RedissonClient Bean
           → RedissonTaskLock 不装配 → 项目正常启动（分布式锁降级）
有 Redis 的环境：RedissonClient 自动配置生效 → RedissonTaskLock 装配 → 锁能力上线
★ 这就是"依赖存在与否决定装配"的经典写法（比 if 判断更干净）
```

### 案例 3：自动配置与自定义 Bean 的"让位"（@ConditionalOnMissingBean）

```text
Spring Boot 的 CacheAutoConfiguration 里：
  @Bean
  @ConditionalOnMissingBean(CacheManager.class)
  public CacheManager cacheManager(...) { ... }   // 默认逻辑

项目若自己定义了 CacheManager Bean → 默认实现不装配（@ConditionalOnMissingBean 让位）
→ 这是 Boot 的核心扩展哲学：【默认给一个，用户覆盖就退让】
```

## 四点半、实测：lite + --debug 的真实 report（2026-11-23）

命令：

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=lite" "-Dspring-boot.run.arguments=--debug"
```

真实输出摘录（Positive / Negative / Exclusions 三段）：

```text
===== Positive matches（命中）=====

CacheAutoConfiguration matched:
   - @ConditionalOnClass found required classes ...

SimpleCacheConfiguration matched:          ★ lite 下缓存降级为内存实现
   - Cache org.springframework.boot.autoconfigure.cache.SimpleCacheConfiguration SIMPLE cache type

KafkaAutoConfiguration matched:            ★ kafka starter 在 classpath
   KafkaAutoConfiguration#kafkaTemplate matched
   KafkaAutoConfiguration#kafkaAdmin matched

RedisAutoConfiguration matched:            ★ redis starter 在 classpath（连接是懒的）
   RedisAutoConfiguration#redisTemplate matched
   RedisAutoConfiguration#stringRedisTemplate matched


===== Negative matches（未命中）=====

CacheConfiguration:                        ★ 我们自己的配置类：条件不满足被跳过
   - @ConditionalOnProperty (app.cache.enabled=true) did not find property 'app.cache.enabled'

RedissonTaskLock:
   - @ConditionalOnProperty (app.lock.redisson=true) did not find property 'app.lock.redisson'


===== Exclusions（被排除）=====

org.redisson.spring.starter.RedissonAutoConfigurationV2    ★ lite yml 的 exclude 生效
```

**读出来的三个结论（把本课讲透了）**：

```text
① @ConditionalOnProperty(app.cache.enabled=false) → 我们的 CacheConfiguration 整个类跳过
   → 但 CacheAutoConfiguration（@ConditionalOnMissingBean(CacheManager)）补上 SimpleCacheManager
   → @Cacheable 依然工作（内存缓存）——"条件装配 + 默认让位"的组合效果

② RedissonAutoConfigurationV2 被 spring.autoconfigure.exclude 排除 → 没有 RedissonClient
   → RedissonTaskLock 的两个条件（@ConditionalOnBean + @ConditionalOnProperty）都不满足 → 不装配
   → 应用照常启动（分布式锁优雅降级）

③ Kafka/Redis 的 AutoConfiguration 是"matched"但不等于"连上了"：
   它们只负责创建 Bean（KafkaTemplate/RedisTemplate）——连接是懒的
   → 所以 lite 下也能启动（配合 spring.kafka.listener.auto-startup=false 等配置）
```
## 五、自动配置的执行顺序（为什么不用你管）

```text
@AutoConfigureBefore(XxxAutoConfiguration.class)   // 我在它之前
@AutoConfigureAfter(YyyAutoConfiguration.class)    // 我在它之后
@AutoConfigureOrder(数字)                          // 同级排序

例子：
  DataSourceAutoConfiguration  ← 必须先有数据源
    ↓
  JpaRepositoriesAutoConfiguration ← 才能扫 Repository
    ↓
  TransactionAutoConfiguration      ← 才能配事务管理器
```

```text
★ 为什么不用你管：Boot 内部用 @AutoConfigureAfter/Before 声明了依赖顺序
   你自定义自动配置时如果需要"先有某个 Bean"，用 @AutoConfigureAfter + @ConditionalOnBean 组合
```

## 六、自定义自动配置的最小骨架（SB2 预告）

```java
// ① 属性类
@ConfigurationProperties(prefix = "my.feature")
public record MyFeatureProperties(boolean enabled, int timeoutSeconds) {}

// ② 自动配置类
@AutoConfiguration
@ConditionalOnClass(MyService.class)          // 依赖类在
@ConditionalOnProperty(prefix = "my.feature", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MyFeatureProperties.class)
public class MyFeatureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean                 // 用户自己定义就退让
    public MyService myService(MyFeatureProperties props) {
        return new MyService(props.timeoutSeconds());
    }
}

// ③ 注册（Boot 2.7+ 的固定位置）
// src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
//   com.example.my.MyFeatureAutoConfiguration
```

```text
SB2 会走完整流程：建一个 research-agent-starter 模块（autoconfigure + starter 两层）+ 测试。
```

## 七、五个常见坑

```text
① 自动配置类不能加 @Component（会被 @ComponentScan 重复扫到）
   → 用 @AutoConfiguration（元注解 @Configuration(proxyBeanMethods=false)）
② 自动配置类不在 @ComponentScan 范围内（它在 imports 清单里，靠 @Import 机制）
③ 条件判断在【类级别】生效：条件不满足 → 整个配置类跳过（不是逐个 @Bean 判断）
④ @ConditionalOnMissingBean 在自动配置类里判断的是"你注册的 Bean"（顺序：你的配置先，自动配置后）
⑤ --debug 的 report 很长（~150 个配置类）→ 用 grep 定位（如 Select-String "RedisAutoConfiguration"）
```

## 八、自测题（答案）

```text
Q1 @SpringBootApplication 由哪三个注解组成？自动配置从哪个注解开始？
Q2 自动配置类清单在 Boot 2.7+ 存在哪个文件里？和老的 spring.factories 什么关系？
Q3 @ConditionalOnMissingBean 解决什么问题？举项目里的例子。
Q4 lite 模式下 @Cacheable 为什么不报错？请用两个条件注解解释。
Q5 你想让"只有引入某个依赖才启用某功能"，该用哪个条件注解？
```

### 答案

### Q1：@SpringBootApplication 由哪三个注解组成？自动配置从哪个注解开始？

**答案（完整）**：

```text
@SpringBootApplication =
    @SpringBootConfiguration     （本质是 @Configuration：标记"这是配置类"，可被组件扫描识别）
  + @ComponentScan               （扫描启动类所在包及子包里的 @Component/@Service/@Controller/@Configuration）
  + @EnableAutoConfiguration     （自动配置入口：@Import(AutoConfigurationImportSelector)）

自动配置的起点 = @EnableAutoConfiguration（不是 @ComponentScan！）
```

**机制展开（装配链的关键类）**：

```text
@EnableAutoConfiguration
  ├── @AutoConfigurationPackage        ← 顺带注册"启动类所在包"作为后续配置的基准包
  └── @Import(AutoConfigurationImportSelector.class)
        ↓ 它是 ImportSelector（返回要导入的配置类名数组）
        ↓ 核心方法链：
        getAutoConfigurationEntry(...)
          ├── getCandidateConfigurations(...)     ← 读 imports 文件（候选清单 ~150 个）
          ├── removeDuplicates(...)               ← 去重
          ├── getExclusions(...)                  ← 处理 @SpringBootApplication(exclude=...) 和
          │                                          spring.autoconfigure.exclude 配置
          └── 交给 ConfigurationClassParser      ← 逐个类做 @Conditional 条件评估
                └── ConditionEvaluator            ← 真正判断"装不装"的地方
```

**项目验证**：

```text
--debug 的 report 里 "Exclusions:" 那一段，就是 getExclusions 的结果：
  org.redisson.spring.starter.RedissonAutoConfigurationV2
  （来自 application-lite.yml 的 spring.autoconfigure.exclude）
```

**易错点**：

```text
① @ComponentScan 与自动配置是【两条独立机制】：
   组件扫描只扫"你的包"；自动配置靠 imports 清单（不扫第三方包）——别混为一谈
② exclude 有两种写法：@SpringBootApplication(exclude=XxxAutoConfiguration.class)
   或 yml 的 spring.autoconfigure.exclude: 全限定名（项目 lite 用的是后者）
③ @SpringBootConfiguration 一个应用只能有一个（它是 @Configuration 的特化，多了会冲突）
```

### Q2：自动配置类清单在 Boot 2.7+ 存在哪个文件？和老的 spring.factories 什么关系？

**答案（完整）**：

```text
新位置（Boot 2.7+ 引入，3.0+ 强制）：
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  格式：每行一个全限定类名（如 com.example.MyAutoConfiguration）

老位置（2.7 及以前）：
  META-INF/spring.factories
  格式：org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
          com.example.A, com.example.B（键值对 + 逗号分隔，多行续写）

关系（2.7 是过渡期）：
  2.7：两个文件都读（新文件优先），同时支持 @AutoConfiguration 注解
  3.0+：只认 imports 文件；@AutoConfiguration 成为标准写法
```

**为什么改（设计原因）**：

```text
① spring.factories 是"大杂烩"：EnableAutoConfiguration/监听器/EnvironmentPostProcessor 等
   全塞一个文件 → 可读性差、按需优化难（无法按"是否 web/是否 bootstrap"分组）
② imports 文件是专用清单：一行为一类，便于 diff/工具解析/条件分组（Boot 3 支持 import 分组）
③ @AutoConfiguration 元注解能在类上直接声明 @AutoConfigureBefore/After（老写法只能写在类上但语义弱）
```

**项目验证**：

```powershell
# 在依赖 jar 里看清单（本项目依赖 spring-boot-autoconfigure-3.5.13.jar）
# 解压后：
#   META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
# 每行一个类名，例如：
#   org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
#   org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
```

**易错点**：

```text
① 自己写 Starter 时还用 spring.factories（3.x 不生效）→ 必须用 imports + @AutoConfiguration
② imports 文件路径必须一字不差（META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports）
   ——放错目录就完全不生效，且不报错（最难查的坑之一，SB2 会亲手验证）
```

### Q3：@ConditionalOnMissingBean 解决什么问题？举项目里的例子。

**答案（完整）**：

```text
语义：容器里【没有】该类型的 Bean 时才装配 —— 给"默认实现"，用户自定义就自动让位。

这是 Spring Boot 的核心扩展哲学：
  默认给你一个能用的 → 你声明自己的 → 默认退让（不用改任何自动配置代码）
```

**机制展开（判断时机与顺序）**：

```text
① 判断发生在【配置类解析阶段】的 ConditionEvaluator 里
② 它检查的是"当前已注册/已解析的 Bean 定义"（不是运行时实例）
③ 顺序至关重要：
   用户配置类（@ComponentScan 扫到的）先解析 → 自动配置类后解析
   → 所以用户的 Bean 能被自动配置"看到"→ @ConditionalOnMissingBean 不成立 → 自动配置让位
   ★ 如果你的用户 Bean 在自动配置【之后】才注册（少见），这个条件会误判
```

**项目里的三个真实例子**（不同方向）：

```text
① 正向：用户覆盖 → 默认让位
   项目 CacheConfiguration 注册了 redisCacheConfiguration(@Bean) 后，
   CacheAutoConfiguration 的 @ConditionalOnMissingBean(CacheManager) 不成立 → 默认 CacheManager 不装配
   （lite 下我们的 CacheConfiguration 被 @ConditionalOnProperty 跳过 → 默认 SimpleCacheManager 才装上）

② 反向：@ConditionalOnBean（有才装）
   RedissonTaskLock：@ConditionalOnBean(RedissonClient.class)
   → 没有 Redisson 就不建这个 Bean（lite 排除了 Redisson 自动配置 → 锁能力降级）

③ 组合：@ConditionalOnClass + @ConditionalOnMissingBean
   RedisAutoConfiguration 的 redisTemplate()：类在 + 用户没定义 → 才装配默认 RedisTemplate
```

**易错点**：

```text
① 类型匹配包含父类/接口：用户定义了 StringRedisTemplate 并不会让 @ConditionalOnMissingBean(RedisTemplate)
   失效（Generic 类型不同）——但 Spring 会做类型推断（注意泛型擦除的边角）
② @ConditionalOnMissingBean 在 @Bean 方法上用 = 只判断该方法的返回类型；
   在类上用 = 需要配合 @Configuration 且判断更宽
③ 条件不满足时【不报错】——这是设计（降级），但排障时要靠 --debug report 才能发现
```

### Q4：lite 模式下 @Cacheable 为什么不报错？请用两个条件注解解释。

**答案（完整链条）**：

```text
第 1 步：lite 的 application-lite.yml 里
         app.cache.enabled: false
         spring.cache.type: simple

第 2 步：项目的 CacheConfiguration（@ConditionalOnProperty(app.cache.enabled=true, matchIfMissing=true)）
         → 条件不满足 → 整个配置类跳过
         → 不创建 RedisCacheConfiguration / RedisCacheManager

第 3 步：Spring Boot 的 CacheAutoConfiguration（@ConditionalOnClass(CacheManager) + 内置分支）
         → 按 spring.cache.type=simple 选择 SimpleCacheConfiguration
         → 它装配 ConcurrentMapCacheManager（内存缓存）
         （@ConditionalOnMissingBean(CacheManager) 此时成立：容器里还没有 CacheManager）

第 4 步：@Cacheable 方法执行时找 CacheManager Bean → 拿到 ConcurrentMapCacheManager
         → 缓存读写落在内存 ConcurrentHashMap → 正常工作（重启后清空）
```

**项目实测证据（--debug report）**：

```text
Positive matches:
  SimpleCacheConfiguration matched       ← 内存缓存被装配
  CacheAutoConfiguration matched

Negative matches:
  CacheConfiguration:                    ← 我们的 @ConditionalOnProperty 配置类跳过
```

**延伸：两种 CacheManager 的行为差异**：

| | lite（Simple/内存） | -Mode redis（Redis） |
|---|---|---|
| 存储位置 | JVM 内存（ConcurrentHashMap） | Redis 服务器 |
| 重启后 | 缓存清空 | 缓存保留（TTL 到期才删） |
| 多实例 | 各自一份（不一致） | 共享一份 |
| 排查 | 本地 debug 可见 | redis-cli 可见 |

**易错点**：

```text
① 以为"没配 Redis 就一定失败"——Boot 会自动降级（这就是 @ConditionalOnMissingBean 的价值）
② lite 下缓存"看起来生效"但多实例/重启后不共享——所以生产必须 Redis
③ Spring Boot 3.x 中 SimpleCacheConfiguration 的 CacheManager 是 ConcurrentMapCacheManager
   （不是 @ConditionalOnMissingBean 显式写在 Simple 上，而是 CacheConfiguration 分支选择）
```

### Q5：想让"只有引入某个依赖才启用某功能"，该用哪个条件注解？

**答案**：`@ConditionalOnClass`

**机制展开**：

```text
实现：OnClassCondition 通过 ClassUtils.isPresent(className, classLoader) 判断
     （只判断"类能不能找到"，不实例化、不加载到可用状态）

为什么这是"依赖存在才启用"的标准做法：
  Maven 依赖 → jar 在 classpath → 类存在 → 条件成立 → 自动配置生效
  没引入依赖 → 类不存在 → 整个自动配置类跳过（不会抛 NoClassDefFoundError）
```

**两种写法（重要区别）**：

```java
// 写法 A：value 直接引类（编译期依赖——注意：本模块必须能编译到该类）
@ConditionalOnClass(RedisOperations.class)

// 写法 B：name 字符串（推荐用于"可选依赖"——类不存在也能编译通过）
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
```

```text
★ 自动配置类通常用写法 B（name 字符串）：
  因为 autoconfigure 模块本身要能编译（不能硬依赖所有可选库）
```

**项目/框架实例**：

```text
RedisAutoConfiguration：
  @ConditionalOnClass({ RedisOperations.class }) → 没引 spring-data-redis 就整类跳过
KafkaAutoConfiguration：
  @ConditionalOnClass({ KafkaTemplate.class })    → 没引 spring-kafka 就跳过
项目的 KafkaErrorHandlingConfig：
  用的是 @ConditionalOnClass 吗？——不是（无条件装配，因为项目一定有 spring-kafka）
  → 说明：条件注解按"可选性"使用，不是必须加
```

**三个条件注解的对比（别混）**：

| 注解 | 判断依据 | 典型场景 |
|---|---|---|
| `@ConditionalOnClass` | **classpath 有没有类**（依赖是否引入） | 引入 redis starter 才自动配置 Redis |
| `@ConditionalOnProperty` | **配置项值**（如 app.cache.enabled） | 功能开关（项目 lite 关缓存） |
| `@ConditionalOnBean` | **容器里有没有 Bean**（运行期依赖） | RedissonTaskLock 要有 RedissonClient 才装 |

**易错点**：

```text
① @ConditionalOnClass 写 value（类字面量）→ 该模块编译期必须有这个依赖；
   给"可选依赖"写 name 字符串（否则编译都过不了，条件永远用不上）
② 条件在类上判断：类里所有 @Bean 一起跳过（不是只跳过一个）
③ @ConditionalOnSecurity / @ConditionalOnWebApplication 等是更专门的变体（本质同族）
```

## 八点五、实验验证：@ConditionalOnMissingBean 的顺序与陷阱（2026-11-23 实测）

### 实验代码（@Profile 隔离，正常启动不生效）

```text
infrastructure/experiment/CacheOverrideExperiment.java   （profiles=lite,cache-experiment）
  自定义一个 CacheManager（与 Boot 默认同类型）
infrastructure/experiment/CacheConflictExperiment.java   （profiles=lite,cache-conflict）
  故意定义两个 CacheManager（cacheManagerA / cacheManagerB）
```

### 实验 1：正常覆盖（用户先 → 默认让位）✅

启动：`mvn spring-boot:run "-Dspring-boot.run.profiles=lite,cache-experiment" "-Dspring-boot.run.arguments=--debug"`

真实输出：

```text
[实验1] 自定义 CacheManager 装配（Boot 的默认 CacheManager 应让位）

CacheAutoConfiguration:
    Did not match:                              ★ 核心证据：自动配置整体未匹配
    - @ConditionalOnMissingBean (names: cacheResolver
        types: org.springframework.cache.CacheManager; SearchStrategy: all)
        found beans                               ← 找到了我们定义的 cacheManager
    Matched:
    - @ConditionalOnClass found required class 'org.springframework.cache.CacheManager'

Started ResearchAgentApplication ✅
```

**读法**：

```text
① CacheAutoConfiguration 从 Positive 变 Did not match → 它的默认 CacheManager 没装配
② 原因行：@ConditionalOnMissingBean ... found beans（"已经有人给了，我退让"）
③ 应用正常启动 → @Cacheable 用我们定义的 CacheManager
```

### 实验 2：陷阱（给两个同类型 Bean）❌

启动：`mvn spring-boot:run "-Dspring-boot.run.profiles=lite,cache-conflict"`

真实输出：

```text
[实验2] cacheManagerA 装配
[实验2] cacheManagerB 装配

WARN ConfigServletWebServerApplicationContext : Exception encountered during context initialization
[INFO] BUILD FAILURE                              ★ 启动失败

（容器里两个 CacheManager → 注入点无法裁决 → context 初始化失败）
```

**结论（这就是"陷阱"）**：

```text
@ConditionalOnMissingBean 只保证"你给了一个，我就退让"；
给两个 → 它退让了，但你的两个没人裁决 → 启动失败。
解决：
  ① @Primary 标记首选（@Bean @Primary CacheManager cacheManagerA()）
  ② 注入点 @Qualifier("cacheManagerA")
  ③ 最简单：只定义一个
```

### 顺序陷阱的原理（为什么"反了"会出问题）

```text
条件判断发生在【配置类解析阶段】（ConditionEvaluator）：
  ① 用户配置类（@ComponentScan 扫描到的）→ 先解析、先注册 BeanDefinition
  ② 自动配置类（AutoConfiguration.imports）→ 后解析
  → 自动配置判断 @ConditionalOnMissingBean 时"已经能看到你的 Bean"→ 退让（实验 1 验证）

如果用户 Bean 在自动配置【之后】才注册：
  · BeanDefinitionRegistryPostProcessor 后置注册
  · 某些 @Import 的延迟配置 / 工厂方法里注册
  → 自动配置先装默认 → 你再注册同类型 → 两个并存 → 同实验 2 的症状（启动失败/注入歧义）
→ 结论：不要依赖"后置注册"去覆盖自动配置；正常 @Configuration + @Bean 就是"先手"。
```
## 九、一句话

```text
自动配置 = 候选清单（imports 文件）+ 条件过滤（@Conditional 家族）+ 默认让位（@ConditionalOnMissingBean）。
读它的三把钥匙：--debug 的 report / /actuator/conditions / 直接看 AutoConfiguration 源码。
记住：Boot 不是"什么都给你配好"，而是"条件满足才配，你覆盖它就退让"。
```
