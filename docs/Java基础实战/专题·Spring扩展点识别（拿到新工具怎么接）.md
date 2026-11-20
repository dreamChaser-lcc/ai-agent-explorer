# 专题 · Spring 扩展点识别——拿到新工具怎么接

> **定位**：横跨 Kafka / Redis / Web / Security 所有集成场景的**母题方法论**——回答一个问题：**"拿到一个新工具，我怎么知道哪些配置能写 yml、哪些要写 Bean、用接口还是注解？"**
> **前置**：J2（接口与多态 / CGLIB 代理）、J7（注解体系）｜**实战关联**：[Redis 08·Spring实战整合](../Redis实战/08·Spring实战整合.md)、[Kafka 监听工厂与错误处理详解](../MQ消息队列实战/Kafka监听工厂与错误处理详解.md)、场景⑥⑦⑨
> **建立**：2026-11-20

---

## 目录

- [1. 为什么这个专题重要](#1-为什么这个专题重要)
- [2. 底层真相：Spring 只有两根接线柱](#2-底层真相spring-只有两根接线柱)
- [3. 五类扩展点：识别清单](#3-五类扩展点识别清单)
- [4. 项目实例逐个拆解](#4-项目实例逐个拆解)
- [5. 拿到新工具的 SOP（6 步实操）](#5-拿到新工具的-sop6-步实操)
- [6. 命名信号词速记（挂载点告示牌）](#6-命名信号词速记挂载点告示牌)
- [7. 全景对照表：五类扩展点 × 项目实例 × Kafka/Redis](#7-全景对照表五类扩展点--项目实例--kafkaredis)
- [8. 常见坑](#8-常见坑)
- [9. 动手实验](#9-动手实验)
- [10. 一句话总结](#10-一句话总结)

---

## 1. 为什么这个专题重要

前端装一个 npm 包的接入方式高度统一：`npm install xxx` → `import { xxx } from 'xxx'` → 直接调用——**库把函数导出来，你拿来就用**。

Java/Spring 世界不一样：引同一个工具，接入姿势可能**五花八门**：

```java
// Kafka：写一个 @Bean 就自动生效（没写一行接线代码）
@Bean
public DefaultErrorHandler kafkaDefaultErrorHandler(...) { ... }

// Web 跨域：实现一个接口
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer { ... }

// 过滤器：加一个注解
@Component
public class TokenAuthFilter extends OncePerRequestFilter { ... }

// 缓存：写几行 yml
spring:
  cache:
    type: redis
```

初学者的困惑就在这里：**为什么接入方式不一样？我怎么知道该用哪种？是不是必须读完整文档？**

本篇的回答：这些方式背后只有**两根接线柱**和**五类挂载点**——识别套路完全通用，文档只需要读固定的两节。

### 1.1 实战演示：以 Spring Security 为例走一遍（企业必用，扩展点最全）

**第①步：starter 与自动配置**

- 引入 `spring-boot-starter-security` → classpath 出现 `SecurityFilterChain`、`UserDetailsService` 等类；
- `SecurityAutoConfiguration` 生效 → **一行代码不写，所有接口立刻要求登录**（Boot 自动生成用户名 `user` + 随机密码打印在控制台）——"引了就自动配置"的铁证。

**第②③步：yml 属性（模式 A 清单）**

- IDE 输入 `spring.security.` 自动补全：`user.name` / `user.password` / `filter.dispatcher-types` 等；
- 属性总表：Spring Boot 官方附录 [Application Properties](https://docs.spring.io/spring-boot/reference/application-properties/index.html) 搜索 `spring.security`。

**第④步：Customizing（模式 B 清单）**

- 文档章节：[Spring Security Reference → Servlet → Java Configuration](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)；
- 信号词出现：`SecurityFilterChain`（Bean）、`UserDetailsService`（回调接口）、`@EnableWebSecurity`（开关）。

**识别结果：五类扩展点在 Security 里的落地**

| 扩展点 | Security 里的样子 |
|--------|------------------|
| yml 属性 | `spring.security.user.name/password`（改默认账号） |
| 覆盖默认 Bean | 自定义 `SecurityFilterChain` Bean → Boot 默认链让位（与 `CacheConfiguration` 让位同款） |
| 回调接口 | `UserDetailsService`（提供用户数据源）、`AuthenticationProvider`（自定义认证逻辑） |
| Enable 开关 | `@EnableWebSecurity`、`@EnableMethodSecurity` |

**最小自定义代码（模式 B 典型形态）**：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()   // 探针放行
                        .anyRequest().authenticated())                 // 其余要登录
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
```

**第⑤⑥步**：IDE 打开 `SecurityAutoConfiguration` / `SpringBootWebSecurityConfiguration` 源码 → 找 `@ConditionalOnMissingBean(SecurityFilterChain.class)`（你写了上面的 Bean，Boot 默认链就退位）→ 试探生效。

**对照记忆**：这个 `SecurityFilterChain` Bean 让位机制，和 `CacheConfiguration` 接管 Redis 缓存配置、手写 `kafkaListenerContainerFactory` 让位 Boot 默认工厂——**是同一个 `@ConditionalOnMissingBean` 逻辑在三个框架里的三次出现**。

### 1.2 常用工具官方文档地址速查表

| 工具 | 参考文档 | yml 属性查这里 | 自定义章节关键词 |
|------|---------|---------------|-----------------|
| Spring Boot 本体 | https://docs.spring.io/spring-boot/reference/ | [Application Properties 附录](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) | 各章 "How-to" |
| **Spring Security** | https://docs.spring.io/spring-security/reference/ | Boot 附录搜 `spring.security` | Servlet → Configuration；"Customizing" |
| **Spring Kafka** | https://docs.spring.io/spring-kafka/reference/ | Boot 附录搜 `spring.kafka` | "Listener Container Customization" |
| **Spring Data Redis** | https://docs.spring.io/spring-data/redis/reference/ | Boot 附录搜 `spring.data.redis` / `spring.cache` | "Redis CacheManager"、"RedisTemplate" |
| Spring Data JPA | https://docs.spring.io/spring-data/jpa/reference/ | Boot 附录搜 `spring.jpa` | "Customizing repositories"？实际为 "Query methods" 等 |
| **WebSocket** | https://docs.spring.io/spring-boot/reference/web/websocket.html | — | `@EnableWebSocket` + `WebSocketConfigurer` |
| **Quartz** | https://docs.spring.io/spring-boot/reference/quartz.html | Boot 附录搜 `spring.quartz` | `SchedulerFactoryBeanCustomizer` |
| Spring AMQP（RabbitMQ） | https://docs.spring.io/spring-amqp/reference/ | Boot 附录搜 `spring.rabbitmq` | "Customizing" |
| MyBatis（社区 starter） | https://github.com/mybatis/spring-boot-starter | README 中的配置表 | Mapper 扫描、`SqlSessionFactory` 定制 |

> 使用方法：拿到新工具 → 先看它的参考文档"Spring Boot 集成"小节（引哪个 starter）→ 再到 Boot 的 Application Properties 附录搜属性名（模式 A 全清单）→ 最后按"Customizing"章节找回调接口（模式 B 清单）——三步走完，接入姿势全部锁定。

---

## 2. 底层真相：Spring 只有两根接线柱

任何第三方框架（Kafka / Redis / Security / MyBatis）与 Spring 的整合，最终都归结为两个基本动作：

```text
① Bean 注册：你往容器里放 Bean（@Component / @Bean / @Import）
② Bean 收集：框架在初始化时 getBeansOfType(某接口) 或按类型注入，
             把你的实现"接线"到它的流程里
```

### 2.1 一个 starter 里装了什么

以 `spring-boot-starter-data-redis` 为例，它其实是两个 jar：

```text
spring-boot-starter-data-redis        ← 空壳：只帮你引入依赖
spring-boot-autoconfigure             ← ★ 自动配置类住在这里（真正的"接入逻辑"）
```

自动配置类长这样（简化）：

```java
@AutoConfiguration
@ConditionalOnClass(RedisOperations.class)          // ① classpath 里有 Redis 类才生效（引了 starter 就有）
@EnableConfigurationProperties(RedisProperties.class) // ② 绑定 yml：spring.data.redis.* → RedisProperties 对象
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)   // ★ 你没写 → 我创建默认的
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties props) {
        return new LettuceConnectionFactory(props.getHost(), props.getPort());
    }

    @Bean
    @ConditionalOnMissingBean(name = "stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

### 2.2 三条铁律（所有 starter 通用）

| 铁律 | 含义 | 你的体验 |
|------|------|---------|
| **@ConditionalOnClass** | classpath 有对应类，自动配置才生效 | 引入 starter = 引入依赖 = 打开开关 |
| **@EnableConfigurationProperties** | yml 的 `spring.data.redis.*` 被绑定成强类型对象 | yml 里的属性有"归属的类"，IDE 才能补全 |
| **@ConditionalOnMissingBean** | **容器里没有同类型 Bean 才创建默认的；你写了，它让位** | `CacheConfiguration` 接管 TTL 后，yml 同类配置失效 |

> **前端类比**：`@ConditionalOnMissingBean` ≈ 函数参数的默认值——`function connect(config = defaultConfig)`。你传了 config 就用你的，没传就用默认。Spring 的"自动装配"就是**框架版默认参数**，而你写的 `@Bean` 就是**传参覆盖**。

---

## 3. 五类扩展点：识别清单

| # | 扩展点 | 判断信号 | 接入姿势 | 项目实例 |
|---|--------|---------|---------|---------|
| 1 | **yml 属性** | IDE 补全 `spring.<工具名>.*`；文档 "Configuration properties" 表格 | 写 yml，不写代码 | `spring.kafka.listener.ack-mode`、`spring.data.redis.*` |
| 2 | **覆盖自动配置 Bean** | `@ConditionalOnMissingBean(X.class)`——同类型 Bean 让位 | `@Bean` 返回同类型对象 | [`CacheConfiguration`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)（接管 RedisCacheConfiguration） |
| 3 | **回调接口** | `implements XxxConfigurer / XxxHandler / XxxListener`（文档说 "implement ... to customize"） | `@Configuration` + 实现接口 | [`WebCorsConfiguration`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/WebCorsConfiguration.java)（WebMvcConfigurer）、`KafkaErrorHandlingConfig`（CommonErrorHandler） |
| 4 | **注解标记 / Servlet 组件** | 特定类型 + `@Component`；特定注解（@KafkaListener/@Scheduled） | `@Component` | [`TokenAuthFilter`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/TokenAuthFilter.java)（Filter + @Component） |
| 5 | **Enable 开关** | `@EnableXxx` 注解 | 在启动类/配置类上加 | `@EnableCaching`（场景⑦） |

**每一类的"规则定义在哪"**：

| 类 | 规则定义处 | 谁来接线 |
|-----|-----------|---------|
| 1 yml 属性 | `application.yml` | Boot 的自动配置类绑定 |
| 2 覆盖 Bean | 你的 `@Bean` 代码 | `@ConditionalOnMissingBean` 让位 |
| 3 回调接口 | 你的接口实现 | 框架 `getBeansOfType(X.class)` 收集后回调 |
| 4 注解标记 | 你的组件类 | Boot 按类型扫描注册 / 框架扫描注解方法 |
| 5 Enable 开关 | 一行注解 | `@Import` 导入框架的激活模块 |

---

## 4. 项目实例逐个拆解

### 4.1 `WebCorsConfiguration implements WebMvcConfigurer`——跨域为什么生效

```java
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) { ... }
}
```

**完整链路**：

```text
① Spring 启动 → 发现这个 @Configuration Bean（类型是 WebMvcConfigurer）
② Boot 的 DelegatingWebMvcConfiguration 收集容器中【所有】WebMvcConfigurer 类型的 Bean
③ DispatcherServlet 初始化时，逐个回调每个 Configurer 的钩子：
     addCorsMappings → 跨域规则注册
     addInterceptors → 拦截器注册
     ...（十几个钩子，实现哪个调哪个）
④ 运行时：请求进来 → CorsMapping 命中 → CORS 头写入响应
```

**为什么"实现接口就能被回调"**：`DelegatingWebMvcConfiguration` 里有一个 `@Autowired(required = false) List<WebMvcConfigurer>`——**Spring 把容器里所有该类型的 Bean 收集成列表**，这就是 §2 的"接线柱②"。

### 4.2 `TokenAuthFilter`——`@Component` 一个 Filter 为什么就进了过滤器链

```java
@Component        // ★ 只要这个注解
@Order(1)
public class TokenAuthFilter extends OncePerRequestFilter { ... }
```

**完整链路**：

```text
① Spring 发现一个 Bean，类型是 jakarta.servlet.Filter
② Boot 的 ServletContextInitializerBeans 收集所有 Filter 类型的 Bean
③ 按 @Order 排序，注册进 Tomcat 的过滤器链
④ 每个请求：先过过滤器链（TokenAuth → BodyCaching → ...）→ 才到 DispatcherServlet
```

**为什么连实现接口的"配置类"都不用**：因为 Filter 是 **Servlet 规范的运行组件**（每个请求都要执行），Boot 的约定是"**容器里的 Filter Bean = 要注册的过滤器**"。而 `WebMvcConfigurer` 是**配置定制器**（只在启动时回调一次），所以需要 `@Configuration`——**不同扩展点的接入姿势不同，以官方示例为准**。

### 4.3 `KafkaErrorHandlingConfig`（CommonErrorHandler）——自动挂载

```text
① @Bean 注册 DefaultErrorHandler（实现 CommonErrorHandler 接口）
② Boot 创建默认 kafkaListenerContainerFactory 时：
     getBeansOfType(CommonErrorHandler.class) → 恰好一个 → factory.setCommonErrorHandler(...)
③ 运行时：@KafkaListener 抛异常 → 重试 3 次 → 进死信主题
```

详见场景⑥ §7.5 与 [Kafka 监听工厂详解](../MQ消息队列实战/Kafka监听工厂与错误处理详解.md)。

### 4.4 `CacheConfiguration`——覆盖自动配置（模式 B 让位模式 A）

```java
@Bean
public RedisCacheConfiguration redisCacheConfiguration() { ... }   // 类型 = RedisCacheConfiguration
```

Boot 默认也创建一个 `RedisCacheConfiguration` Bean（无 TTL + JDK 序列化），但带 `@ConditionalOnMissingBean`——**你写了一个同类型 Bean，Boot 让位**。让位后默认值失效（JDK 序列化→JSON、无 TTL→30 分钟），yml 的 `spring.cache.redis.*` 同类配置也不再生效。

---

## 5. 拿到新工具的 SOP（6 步实操）

```text
① 看有没有官方 starter（文档 "Spring Boot Integration" 章节）
      → 引入后 classpath 有类 = 自动配置开始工作

② IDE 打开 application.yml，输入 spring.<工具名>.
      → 自动补全列出所有 yml 属性（模式 A 全清单）
      （底层：jar 里的 spring-configuration-metadata.json——IDE 补全的数据源）

③ 查官方文档附录 "Application Properties"
      → 每个属性的含义与默认值

④ 在文档目录搜 "Customizing / Custom" 关键词
      → 定位扩展点章节（模式 B 清单）

⑤ IDE 打开 XAutoConfiguration 类
      → 看 @ConditionalOnMissingBean（可覆盖项）和 getBeansOfType（可收集项）

⑥ 写一个实现/Bean 试探
      → 生效 = 猜对了；没生效 = 回②查属性拼写、缺 @EnableXxx、或多 Bean 歧义
```

### 5.1 第①步详解：classpath 在哪看 + 自动配置清单怎么理解（2026-11-20 补）

#### 一、先破概念：classpath 不是一个文件，是"查找范围"

JVM 运行时找 `.class` 的所有位置总和，由两部分构成：

```text
你的项目编译输出：backend/target/classes/           ← 你写的代码 + application.yml 编译副本
+ 所有依赖 jar：~/.m2/repository/ 里的几百个 jar     ← Maven 拉下来的依赖
```

**4 种查看方式**（从直观到底层）：

| 方式 | 看到什么 |
|------|---------|
| ① IDE 的 **External Libraries** 面板（最直观） | 展开任何 jar（如 `spring-boot-autoconfigure:3.2.4`）→ 能看到一个个 `.class` 文件——**classpath 的可视化窗口**，双击可直接打开框架源码/配置文件 |
| ② Project Structure → Modules → Dependencies | 当前模块的依赖列表（= classpath 构成清单） |
| ③ `mvn dependency:tree` | 命令行依赖树：哪些 jar、被谁传递引入（排查冲突类必备） |
| ④ `/actuator/conditions` 端点 | 运行时视角：哪些自动配置**生效/被跳过**（classpath 影响的结果） |

#### 二、自动配置"清单"是真实存在的文件

**位置**（就在依赖 jar 内部）：

```text
External Libraries
└── spring-boot-autoconfigure:3.2.4
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports   ← ★ 清单本体
```

打开它，内容是每行一个类名（Boot 3.2 有 140+ 行）：

```text
org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
...（140+ 行）
```

> Boot 2.7 之前清单在 `META-INF/spring.factories`，2.7+ 换成 imports 文件——网上老文章讲 `spring.factories` 的要留意版本。

#### 三、"扫描"的完整过程（启动序列 6 步）

```text
① JVM 启动，classpath 就绪（你的 target/classes + 所有依赖 jar 可被加载）
② Boot 读清单：汇总所有 jar 的 ...AutoConfiguration.imports → 得到 140+ 个自动配置类名
   （★ 不是"扫描你的类"，是"读打包时就写死的名单"）
③ 注册你的组件：扫描你写的 @Component/@Service/@Configuration（组件扫描）
④ 逐个评估自动配置类的"入职条件"：
     @ConditionalOnClass(RedisOperations.class)      → 试加载该类 → 成功 = classpath 有 = 通过
     @ConditionalOnMissingBean(RedisConnectionFactory.class) → 看容器里有没有你的 Bean
     @ConditionalOnProperty(name="app.cache.enabled") → 看 yml
   ✅ 全部满足 → 生效    ❌ 有一个不满足 → 整个类跳过
⑤ 生效的自动配置 → 执行其中的 @Bean → 工厂/模板/错误处理器进入容器
⑥ 应用就绪（/actuator/conditions 可回看全过程）
```

**关键理解修正**："扫描"不是漫无目的地翻文件——**是读一份打包时写死的名单，再逐个做条件判断**。你的代码影响它的方式只有三种：classpath（引没引依赖）、容器里的 Bean（写没写自定义 Bean）、yml（配没配属性）。

#### 四、类比：插件目录 + 自启动清单

```text
classpath                        ≈ 程序的"插件目录"（装了什么插件 = 依赖树）
AutoConfiguration.imports        ≈ 插件目录里的"自启动清单"（每个插件声明"我是自启动项"）
条件评估                          ≈ 每个自启动项启动前自查：
                                    "用户装了我的依赖吗（OnClass）？
                                     用户没自己写配置吗（OnMissingBean）？
                                     用户没禁用我吗（OnProperty）？"
生效创建 Bean                     ≈ 自启动成功，功能就位
```

往 `pom.xml` 加一个 starter ≈ 往插件目录装一个插件——**下次开机（重启应用），清单里的对应项自动生效**。这就是"Java 项目加一个依赖就多了好多功能"的机制。

#### 五、亲眼看一遍（5 分钟实验）

1. **看清单文件**：IDE 侧栏 External Libraries → `spring-boot-autoconfigure` → `META-INF/spring/` → 打开 imports 文件 → 搜 `Redis`、`Kafka`，定位你项目用到的自动配置类在清单的哪几行；
2. **看评估结果**：启动应用（`--debug` 或访问 `/actuator/conditions`）→ 找 `RedisAutoConfiguration matched`（生效）和某个没引的（如 `RabbitAutoConfiguration did not match`，原因 = `class not found`）；
3. **动手改变条件**：把 `spring-boot-starter-data-redis` 从 pom 注释掉 → 重启 → conditions 里 Redis 变 `did not match`（class not found）→ 恢复。

走完这一轮，"**classpath 有类 = 自动配置开始工作**"就是肉眼可见的事实。

**文档要读吗？要——但只读固定的两节**：每个 Spring 生态工具的文档结构高度统一（Install → Configuration properties → Customization → Advanced）。跳过其他，**只读 `Configuration properties` 和 `Customizing/Customization` 两节**，10 分钟扫完即可。

---

## 6. 命名信号词速记（挂载点告示牌）

Spring 生态的命名传统：**这些后缀 = "这是给你实现的扩展点"的高概率信号**。

| 后缀 | 含义 | 例子 |
|------|------|------|
| `Configurer` | 批量定制配置 | `WebMvcConfigurer`、`CachingConfigurer` |
| `Customizer` | 单点定制 | `RestTemplateCustomizer`、`KafkaTemplateCustomizer` |
| `Handler` | 某类事件的处理者 | `CommonErrorHandler`、`HandlerMethodArgumentResolver` |
| `Listener` | 事件监听 | `ApplicationListener`、`ConsumerRebalanceListener` |
| `Interceptor` | 流程拦截 | `HandlerInterceptor`、`ClientHttpRequestInterceptor` |
| `Resolver` | 解析策略 | `HandlerMethodArgumentResolver` |
| `Adapter` | 简化/适配实现 | `WebMvcConfigurerAdapter`（已废弃，被接口默认方法取代） |
| `Aware` | 容器反向注入 | `ApplicationContextAware`、`BeanNameAware` |
| `Registrar` | 动态批量注册 | `ImportBeanDefinitionRegistrar` |

**文档里的三句信号话术**（出现即锁定扩展点）：

```text
"You can provide a bean of type X to customize ..."
"Customizing the ..."
"Spring Boot will use ... automatically if ..."
```

---

## 7. 全景对照表：五类扩展点 × 项目实例 × Kafka/Redis

| 扩展点 | 项目实例 | Kafka 侧 | Redis 侧 |
|--------|---------|---------|---------|
| 1 yml 属性 | `spring.kafka.listener.ack-mode` | ✅ `spring.kafka.listener.*` | ✅ `spring.data.redis.*` |
| 2 覆盖自动 Bean | `CacheConfiguration` | 手写 `kafkaListenerContainerFactory`（让位默认） | `RedisCacheConfiguration`（让位默认） |
| 3 回调接口 | `WebCorsConfiguration`、`CommonErrorHandler`、`CachingConfigurer` | `CommonErrorHandler`（Boot 自动收集） | `CacheErrorHandler`（实现 CachingConfigurer） |
| 4 注解标记 | `TokenAuthFilter`、`TaskExecutionConsumer`(@KafkaListener)、`PermissionInterceptor` | `@KafkaListener` | — |
| 5 Enable 开关 | `@EnableCaching` | — | `@EnableCaching` |

---

## 8. 常见坑

| # | 坑 | 现象 | 解法 |
|---|-----|------|------|
| 1 | 手写 Bean 后忘了手动接线 | 手写 kafkaListenerContainerFactory 但异常无限重投 | 模式 B 必须显式 set（自动装配已退位） |
| 2 | `@Configuration` 不在扫描范围 | Bean 注册了但框架没收集 | 主类同包树下，或 `@ComponentScan` 指定 |
| 3 | yml 属性写了不生效 | 拼写错/类型错/空壳节点 | 用 IDE 补全写；对照官方 Properties 附录 |
| 4 | 同类型多个 Bean 歧义 | 自动收集要求"恰好一个" | `@Primary` 标主选，或只留一个 |
| 5 | Filter/Interceptor 顺序错乱 | 鉴权在缓存包装之后执行 | `@Order` 显式排序（TokenAuth=1） |
| 6 | 自定义工厂不吃 `spring.kafka.listener.*` | listener 段配置没生效 | 模式 B 要自己应用属性（详见 Kafka 详解 §3） |

---

## 9. 动手实验

1. **观察自动补全**：application.yml 输入 `spring.redis` → 看 IDE 提示的属性树（= metadata 清单）；
2. **写一个自定义 WebMvcConfigurer**：加一个拦截器打印请求耗时，对比 Filter 的执行时机（Filter 更早）；
3. **覆盖一个自动 Bean**：写一个自定义 `StringRedisTemplate` Bean，启动日志/行为验证 Boot 让位；
4. **读一个 XAutoConfiguration 源码**：IDE 搜 `KafkaAutoConfiguration`，找出全部 `@ConditionalOnMissingBean`——每个都是一个"可覆盖点"。

---

## 10. 一句话总结

```text
Spring 生态的扩展模型只有两个动作：你注册 Bean，框架按类型/名字收集接线。
五类挂载点（yml 属性 / 覆盖默认 Bean / 回调接口 / 注解标记 / Enable 开关）
+ 命名信号词（Configurer/Handler/Listener/Interceptor）
+ 文档固定两节（Configuration properties / Customizing）
= 拿到任何新工具，10 分钟定位接入姿势。
```
