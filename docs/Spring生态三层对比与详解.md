# Spring MVC / Spring Boot / Spring Cloud 区别对比与详解

> 适用版本：Spring Boot 3.5（本项目）、Spring Boot 4.x / Spring Cloud 2025.x（最新主线）
> 最后更新：2026-08-30

---

## 目录

- [一、整体认知：三者不在同一层](#一整体认知三者不在同一层)
- [二、Spring MVC 详解](#二spring-mvc-详解)
- [三、Spring Boot 详解](#三spring-boot-详解)
- [四、Spring Cloud 详解](#四spring-cloud-详解)
- [五、三者对比速查表](#五三者对比速查表)
- [六、结合本项目](#六结合本项目)
- [七、学习路径建议](#七学习路径建议)

---

## 一、整体认知：三者不在同一层

```
Spring Framework（IoC 容器 + AOP，是整个生态的地基）
   │
   ├── Spring MVC      ← 一个「Web 层模块」，解决"如何处理 HTTP 请求"
   │
   ├── Spring Boot     ← 一个「开发/装配脚手架」，解决"如何少写配置把应用跑起来"
   │      └── 内部默认集成 Spring MVC，并内嵌 Tomcat
   │
   └── Spring Cloud    ← 一个「分布式系统工具集」，解决"多个 Boot 应用之间怎么治理"
          └── 必须跑在 Spring Boot 之上，它本身不是容器也不是框架
```

### 一句话区分

| 技术 | 一句话 |
|---|---|
| **Spring MVC** | 写接口用的（`@RestController` 那一套） |
| **Spring Boot** | 让项目能 `main()` 直接跑起来、不用配 XML、不用外挂 Tomcat 的那一套 |
| **Spring Cloud** | 当 Boot 应用从一个变成几十个时，管注册发现、配置、网关、熔断、链路追踪的那一套 |

### 关键关系

- Boot 和 MVC **不是替代关系**，Boot **包含并自动装配** MVC。
- Cloud 和 Boot 是**强版本绑定**关系，版本选错直接启动失败（见 [4.4 版本对应](#44-版本对应强绑定选错直接启动失败)）。

---

## 二、Spring MVC 详解

### 2.1 它是什么

Spring Framework 的一个模块（`spring-webmvc`），基于 **Servlet API** 的 **前端控制器（Front Controller）模式** Web 框架。核心是 `DispatcherServlet` —— 所有请求先打到它，由它统一分发。

> 本项目中的对应位置：`backend/src/main/java/com/aiexplorer/researchagent/api/controller/ResearchTaskController.java`

### 2.2 请求全流程（核心中的核心）

以 `GET /api/tasks/123` 为例，`DispatcherServlet#doDispatch()` 内部：

```
① 请求 → DispatcherServlet.doDispatch()
② getHandler(request)
     └─ 遍历所有 HandlerMapping，匹配到 @RequestMapping("/api/tasks/{taskId}")
        → 返回 HandlerExecutionChain（= HandlerMethod + 拦截器链）
③ getHandlerAdapter(handler)
     └─ 找到能执行它的适配器（RequestMappingHandlerAdapter）
④ 执行拦截器 preHandle()          ← 权限校验、日志 traceId 通常放这
⑤ 真正调用 Controller 方法
     ├─ HandlerMethodArgumentResolver：把 HTTP 请求"翻译"成方法参数
     │     @PathVariable / @RequestParam / @RequestBody / @RequestHeader ...
     │     （@RequestBody 依赖 HttpMessageConverter 做 JSON ↔ 对象转换，如 Jackson）
     └─ 反射执行方法
⑥ 处理返回值
     └─ HandlerMethodReturnValueHandler
        @ResponseBody → 用 HttpMessageConverter 序列化成 JSON 直接写出
        返回 String   → 交给 ViewResolver 渲染页面（传统 MVC 模式）
⑦ 若抛异常 → HandlerExceptionResolver
     └─ @RestControllerAdvice + @ExceptionHandler 就挂在这里
⑧ 拦截器 postHandle()
⑨ 渲染/写响应 → 拦截器 afterCompletion()（无论成功失败都会走）
```

### 2.3 九个扩展点接口

| 接口 | 作用 |
|---|---|
| `HandlerMapping` | URL → 处理器 的映射 |
| `HandlerAdapter` | 真正执行处理器（屏蔽各种 handler 类型差异） |
| `HandlerMethodArgumentResolver` | 参数绑定（自定义注解解析参数就实现它） |
| `HandlerMethodReturnValueHandler` | 返回值处理 |
| `HttpMessageConverter` | 请求体/响应体的序列化（Jackson、Protobuf…） |
| `ViewResolver` | 视图解析（前后端分离项目基本用不到） |
| `HandlerExceptionResolver` | 全局异常处理 |
| `HandlerInterceptor` | 拦截器 |
| `LocaleResolver` / `ThemeResolver` | 国际化/主题（很少用） |

### 2.4 三层拦截对比（不要混用）

```
Filter（Servlet 规范，Tomcat 层）→ Interceptor（Spring MVC 层）→ AOP（Bean 方法层）
```

| | Filter | Interceptor | AOP |
|---|---|---|---|
| 所属层 | Servlet 容器 | Spring MVC | Spring 容器（Bean 代理） |
| 能拿到 | 原始 request/response | handler 对象 | 方法参数与返回值 |
| 拿不到 | Controller 信息 | 方法参数值 | HTTP 对象 |
| 典型用途 | 编码、CORS、粗粒度鉴权 | 登录校验、日志 | 事务、权限注解、审计 |

### 2.5 常用注解

| 注解 | 说明 |
|---|---|
| `@Controller` | 传统控制器，返回视图名 |
| `@RestController` | = `@Controller` + `@ResponseBody`，返回值直接序列化为 JSON |
| `@RequestMapping` | 类/方法级路径映射 |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping` | 语义化的 `@RequestMapping(method=...)` |
| `@PathVariable` / `@RequestParam` / `@RequestBody` / `@RequestHeader` / `@CookieValue` | 参数绑定 |
| `@ResponseBody` | 返回值写入响应体 |
| `@ControllerAdvice` / `@RestControllerAdvice` | 全局异常处理、全局数据绑定 |
| `@ExceptionHandler` | 标注异常处理方法 |
| `@CrossOrigin` | 单接口跨域 |
| `@Valid` / `@Validated` | 参数校验（配合 Bean Validation） |

`@RequestMapping` 的匹配维度：`value/path`、`method`、`params`、`headers`、`consumes`（请求 Content-Type）、`produces`（响应 Content-Type，同时参与 `Accept` 协商与编码设置）。

### 2.6 Spring MVC vs Spring WebFlux

| | Spring MVC | Spring WebFlux |
|---|---|---|
| 编程模型 | 命令式、阻塞 | 响应式、非阻塞 |
| 底层 | Servlet API + 线程池 | Reactor + Netty（也可跑在 Servlet 3.1+ 上） |
| 适用场景 | 常规 CRUD、含 JDBC/JPA 的业务 | 高并发 IO 密集、流式推送 |
| 注意事项 | — | 响应式必须**全链路非阻塞**，中间夹一个阻塞 JDBC 等于白做 |

> 建议：绝大多数业务系统用 MVC。WebFlux 只在确实需要且能保证全链路响应式时才上。
>（Spring Cloud Gateway 本身就是基于 WebFlux 的，这是它少数合理的用武之地。）

### 2.7 SSE 与 MVC 的异步支持

本项目 `ResearchTaskController` 中的 `/{taskId}/stream` 返回 `SseEmitter`，依赖 MVC 的异步请求处理能力（`AsyncHandlerInterceptor` / `WebAsyncManager`），本质是把请求线程释放、由业务线程异步写回响应。

---

## 三、Spring Boot 详解

### 3.1 它是什么

**不是新框架**，而是「约定优于配置」的快速开发框架。本质是三件事：

1. **起步依赖（starter）**：一个依赖 = 一组经过版本验证的依赖集合，消灭依赖地狱。
2. **自动配置（auto-configuration）**：classpath 里有什么 jar，就自动帮你配好对应的 Bean。
3. **内嵌容器 + 生产特性**：打成 fat jar，`java -jar` 直接跑；Actuator 提供健康检查、指标、监控端点。

### 3.2 `@SpringBootApplication` 的真身

```java
@SpringBootApplication
 = @SpringBootConfiguration  // 就是 @Configuration，主类本身是个配置类
 + @EnableAutoConfiguration  // ★ 核心：开启自动配置
 + @ComponentScan            // 扫描主类所在包及其子包下的 @Component 等
```

### 3.3 自动配置原理（最重要的一块）

```
@EnableAutoConfiguration
  └─ @Import(AutoConfigurationImportSelector.class)
       └─ 读取 classpath 下所有 jar 里的：
          META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
          （Boot 2.7 之前是 META-INF/spring.factories，3.x 起已迁移）
       └─ 拿到几百个自动配置类的全限定名，过滤、去重、排序后导入 IoC 容器
```

**为什么不会全量生效？** 因为每个自动配置类上都挂了**条件注解**：

| 条件注解 | 含义 |
|---|---|
| `@ConditionalOnClass` | classpath 存在某个类才生效（如有 `DataSource` 类才配数据源） |
| `@ConditionalOnMissingBean` | 容器里**没有**用户自定义的同类 Bean 才生效（★ 这是"用户配置永远优先"的原因） |
| `@ConditionalOnProperty` | 配置文件中某个属性匹配才生效（如 `spring.mvc.*`） |
| `@ConditionalOnBean` | 存在某个 Bean 才生效 |
| `@ConditionalOnWebApplication` | Web 环境才生效 |

**顺序控制**：`@AutoConfiguration(before = Xxx.class, after = Yyy.class)`、`@AutoConfigureOrder`。
顺序很重要，因为条件判断依赖先后（例如 `DataSourceAutoConfiguration` 必须在 JPA 自动配置之前）。

**一句话总结**：
> starter 把 jar 拉进来 → `AutoConfiguration.imports` 声明候选配置 → 条件注解决定生不生效 → `@ConditionalOnMissingBean` 保证用户配置可覆盖。

### 3.4 启动流程（精简版）

```
SpringApplication.run(App.class, args)
 ① 推断应用类型：SERVLET / REACTIVE / NONE
 ② 加载 ApplicationContextInitializer、ApplicationListener（事件机制）
 ③ 准备 Environment，加载所有 PropertySource（命令行、环境变量、application.yml…）
 ④ 创建 ApplicationContext（Servlet 环境为 AnnotationConfigServletWebServerApplicationContext）
 ⑤ prepareContext：注册主类的 BeanDefinition
 ⑥ refreshContext：执行 BeanFactoryPostProcessor → 实例化单例 Bean
      └─ 其中 onRefresh() 时 createWebServer()：由 ServletWebServerFactory
         创建内嵌 Tomcat（默认）并启动 → 这就是不需要外部 Tomcat 的原因
 ⑦ 调用 ApplicationRunner / CommandLineRunner（启动后初始化逻辑放这里）
 ⑧ 发布 ApplicationReadyEvent
```

切换容器：排除 `spring-boot-starter-tomcat`，引入 `spring-boot-starter-jetty` 或 `spring-boot-starter-undertow`。

### 3.5 外部化配置优先级（高 → 低，常用档位）

```
1. 命令行参数                 java -jar app.jar --server.port=8081
2. 操作系统环境变量            SERVER_PORT=8081（松散绑定：server.port ↔ SERVER_PORT）
3. jar 包【外部】的 application-{profile}.yml
4. jar 包【内部】的 application-{profile}.yml
5. jar 包【外部】的 application.yml          ← 运维覆盖配置的常用手段
6. jar 包【内部】的 application.yml
7. @PropertySource 注解指定的文件
8. SpringApplication.setDefaultProperties()
```

配套机制：

- `@ConfigurationProperties`：类型安全地绑定一组配置，**优于散落的 `@Value`**
- `@Profile` + `spring.profiles.active`：环境隔离
- 松散绑定：`server.port` ↔ `SERVER_PORT` ↔ `serverPort`
- 配置导入：`spring.config.import`（Boot 2.4+）

### 3.6 常用 starter

| Starter | 作用 |
|---|---|
| `spring-boot-starter-web` | Spring MVC + 内嵌 Tomcat + Jackson |
| `spring-boot-starter-webflux` | WebFlux + Netty |
| `spring-boot-starter-data-jpa` | Spring Data JPA + Hibernate |
| `spring-boot-starter-data-redis` | Redis（Lettuce 客户端） |
| `spring-boot-starter-validation` | Bean Validation（Hibernate Validator） |
| `spring-boot-starter-security` | Spring Security |
| `spring-boot-starter-actuator` | 健康检查、指标、监控端点 |
| `spring-boot-starter-test` | JUnit 5 + Mockito + AssertJ + Spring Test |

### 3.7 Actuator 常用端点

| 端点 | 用途 |
|---|---|
| `/actuator/health` | 健康检查（K8s 探针依赖） |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 指标（Micrometer） |
| `/actuator/env` | 环境变量（注意脱敏） |
| `/actuator/beans` | 所有 Bean（排查自动配置是否生效的神器） |
| `/actuator/conditions` | **自动配置匹配报告**（看某个配置为什么没生效） |
| `/actuator/threaddump` / `/actuator/heapdump` | 线程/堆快照 |

### 3.8 Boot 3 / Boot 4 的关键变化

| 变化 | 说明 |
|---|---|
| JDK 基线提升 | Boot 3 要求 Java 17+，Boot 4 要求更高版本 |
| `javax.*` → `jakarta.*` | Servlet 包名全变，这是 2.x 升 3.x 最大的破坏性变更 |
| 自动配置注册文件迁移 | `spring.factories` → `META-INF/spring/.../AutoConfiguration.imports` |
| 可观测性统一 | Micrometer + Micrometer Tracing（Spring Cloud Sleuth 已停维护） |
| AOT / GraalVM Native Image | 启动毫秒级、内存占用低，适合 Serverless；反射/动态代理需额外配置 |

> 当前最新版本（取自 spring.io，2026-08）：Spring Boot **4.1.1** 为最新 GA。

---

## 四、Spring Cloud 详解

### 4.1 它是什么

基于 Spring Boot 的**分布式系统/微服务工具集**（官方定位：tools for common patterns in distributed systems）。它不是一个可运行的框架，而是一堆独立子项目的 **BOM 集合**，按需引入。

它解决的是：**当单体拆成 N 个服务后，那些每个服务都要重复写的基础设施代码**。

### 4.2 能力地图与组件选型

| 能力 | 官方/主流方案 | 国内主流 | 说明 |
|---|---|---|---|
| 服务注册与发现 | Eureka、Consul、Zookeeper、K8s Service | **Nacos** | 服务启动注册地址，调用方按服务名寻址 |
| 配置中心 | Spring Cloud Config（Git 后端） | **Nacos Config**、Apollo | 集中配置 + 动态刷新（`@RefreshScope`） |
| API 网关 | **Spring Cloud Gateway** | Gateway / Higress | 统一入口：路由、鉴权、限流、跨域 |
| 服务调用 | **OpenFeign** | OpenFeign | 声明式 HTTP 客户端，写接口 + 注解即可调用 |
| 负载均衡 | **Spring Cloud LoadBalancer** | 同上 | 替代已停维护的 Ribbon |
| 熔断降级限流 | **Resilience4j** | **Sentinel** | 替代已停维护的 Hystrix |
| 链路追踪 | **Micrometer Tracing**（+ Zipkin/OTLP） | SkyWalking | 替代已停维护的 Spring Cloud Sleuth |
| 消息驱动 | Spring Cloud Stream | Kafka / RocketMQ | 屏蔽 MQ 差异，统一编程模型 |
| 分布式事务 | — | **Seata** | AT / TCC / Saga 模式 |
| 短生命周期任务 | Spring Cloud Task | — | 批处理型微服务 |

### 4.3 核心组件深入

#### Spring Cloud Gateway（响应式网关）

三要素：

- **Route（路由）**：`id` + 目标 `URI` + 断言集合 + 过滤器集合
- **Predicate（断言）**：匹配条件 —— `Path`、`Method`、`Header`、`Query`、`After`/`Before`（定时上线）、`Weight`（灰度权重）
- **Filter（过滤器）**：`GatewayFilter`（单路由）与 `GlobalFilter`（全局）—— 实现鉴权、限流（`RequestRateLimiter` + Redis 令牌桶）、`RewritePath`、`Retry`、`CircuitBreaker`

底层是 **WebFlux + Netty**，非阻塞。因此它**不能**同时引入 `spring-boot-starter-web`（会冲突启动失败），只能用 `spring-boot-starter-webflux`。

#### OpenFeign

```
接口 + @FeignClient(name = "user-service")
  → 启动时由 FeignClientFactoryBean 生成 JDK 动态代理
  → 调用时 InvocationHandler 把方法签名转成 HTTP 请求（Contract 解析注解）
  → 服务名经 LoadBalancer 换成真实 IP:Port
  → Encoder/Decoder 序列化，Client（默认 JDK HttpURLConnection，可换 OkHttp/Apache HC）发出
  → 可挂 Resilience4j / Sentinel 做降级（fallback）
```

注意点：Feign 的超时（`connectTimeout` / `readTimeout`）**必须显式配置**，默认往往过长，是生产事故常见来源。

#### Nacos

双能力合一：

- **注册中心**：AP/CP 可切换，临时实例走 AP 心跳，持久实例走 CP（Raft）
- **配置中心**：`dataId` / `group` / `namespace` 三级隔离 + 长轮询实现动态刷新

### 4.4 版本对应（强绑定，选错直接启动失败）

Spring Cloud 采用 **Release Train（火车发布）** 命名，必须与 Boot 版本严格匹配（数据来源：spring.io，2026-08）：

| Spring Cloud Release Train | 对应 Spring Boot | 状态 |
|---|---|---|
| **2025.1.x（Oakwood）** | 4.0.x、4.1.x（从 2025.1.2 起） | 当前主线 |
| **2025.0.x（Northfields）** | 3.5.x | 支持中 |
| 2024.0.x（Moorgate） | 3.4.x | **EOL** |
| 2023.0.x（Leyton） | 3.2.x、3.3.x | **EOL** |
| 2022.0.x（Kilburn） | 3.0.x、3.1.x | **EOL** |
| 2021.0.x（Jubilee） | 2.6.x、2.7.x | **EOL** |
| 2020.0.x（Ilford） | 2.4.x、2.5.x | **EOL** |
| Hoxton / Greenwich / Finchley / Edgware / Dalston | 2.2~1.5.x | **EOL** |

> 2024.0.x 及更早的所有 train 均已停止支持（EOL）。

引入方式（用 BOM 统一管理，**不要在子依赖上写版本号**）：

```xml
<properties>
  <spring-cloud.version>2025.0.x</spring-cloud.version>   <!-- Boot 3.5 → 选这个 -->
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

若使用 Spring Cloud Alibaba，还需额外匹配 **SCA 版本 ↔ Cloud 版本 ↔ Boot 版本** 三层关系。

### 4.5 Netflix 系 vs Alibaba 系

| 能力 | Spring Cloud Netflix | Spring Cloud Alibaba |
|---|---|---|
| 注册中心 | Eureka（**已停止维护**） | **Nacos** |
| 配置中心 | Spring Cloud Config | **Nacos Config** |
| 熔断限流 | Hystrix（**已停止维护**） | **Sentinel**（带控制台，规则可视化） |
| 网关 | Zuul（已淘汰）/ Gateway | Gateway |
| 调用 | OpenFeign | OpenFeign |
| 分布式事务 | — | **Seata** |
| 现状 | 组件大量进入维护模式/停更 | 国内主流，社区活跃 |

---

## 五、三者对比速查表

| 维度 | Spring MVC | Spring Boot | Spring Cloud |
|---|---|---|---|
| 本质 | Web 框架模块 | 快速开发脚手架 | 分布式治理工具集 |
| 解决的问题 | 如何处理 HTTP 请求 | 如何快速搭建单体应用 | 如何治理多服务集群 |
| 依赖关系 | 依赖 Spring Framework | 依赖 Spring Framework（含 MVC） | 依赖 Spring Boot |
| 部署产物 | 需部署到外部 Servlet 容器（WAR） | 可执行 fat jar，内嵌容器 | 一组能力依赖，无独立产物 |
| 核心机制 | `DispatcherServlet` + 组件扩展点 | 自动配置 + 条件注解 + starter | BOM + 自动装配各中间件客户端 |
| 是否必须 | 写 Web 接口时"必须"（或用 WebFlux） | 现代 Spring 项目事实上的必须 | 只有微服务化才需要 |
| 学习成本 | 中 | 低（会配就行，但原理要懂） | 高（组件多、运维重） |

### 常见误区澄清

| 误区 | 正解 |
|---|---|
| "Spring Boot 是 Spring MVC 的升级版" | 错。Boot 是脚手架，内部**包含并自动装配** MVC，两者是包含关系 |
| "用了 Boot 就不用学 MVC 了" | 错。Boot 只是帮你把 MVC 配好了，`@RequestMapping`、参数绑定、异常处理仍是 MVC 的知识 |
| "微服务就该上 Spring Cloud" | 不一定。服务数量少（<5）时，K8s + 简单 HTTP 调用往往比全套 Cloud 更轻 |
| "Spring Cloud 能脱离 Boot 使用" | 不能。Cloud 是建立在 Boot 的自动配置机制之上的 |
| "用了 WebFlux 性能一定更好" | 不一定。全链路必须非阻塞，任何一个阻塞调用都会拖垮整体 |

---

## 六、结合本项目

本项目 `backend` 是典型的 **Spring Boot 3.5 + Spring MVC 单体应用**，目前 **没有引入 Spring Cloud**（当前阶段也不必引入）。

### 已用到的 MVC 能力

| 位置 | 用到的 MVC 知识 |
|---|---|
| `api/controller/ResearchTaskController.java` | `@RestController`、`@RequestMapping`、`@GetMapping`/`@PostMapping`、`@PathVariable`、SSE（`SseEmitter`） |
| `api/controller/ResearchReportController.java` | 嵌套路径映射 `/api/tasks/{taskId}/report` |
| `api/controller/ApiExceptionHandler.java` | `@RestControllerAdvice` + `@ExceptionHandler`（对应请求流程第 ⑦ 步的异常解析器） |
| `api/controller/HealthController.java` | 健康检查接口 |

### 已用到的 Boot 能力

| 位置 | 用到的 Boot 知识 |
|---|---|
| `ResearchAgentApplication.java` | `@SpringBootApplication` 启动类 |
| `application-dev.yml` | 外部化配置 + Profile 隔离 |
| H2 数据源 | `DataSourceAutoConfiguration` 自动配置（无需手写 `DataSource` Bean） |
| SSE 实时推送 | 依赖内嵌 Tomcat 的异步请求支持 |
| Flyway 在 dev 下禁用 | `@ConditionalOnProperty` 类条件化配置 |

### 后续若要微服务化

若将来需要拆分（例如把「LLM 编排执行」独立成计算服务 —— 它耗时长、需要独立扩缩容），再考虑引入 Cloud：

1. **Nacos**：注册中心 + 配置中心
2. **Spring Cloud Gateway**：统一入口、鉴权、限流
3. **OpenFeign**：服务间调用
4. **Resilience4j / Sentinel**：熔断降级
5. **Micrometer Tracing**：链路追踪

此时 Boot 3.5 应搭配 **Spring Cloud 2025.0.x（Northfields）**。

---

## 七、学习路径建议

1. **Spring Framework 核心**：IoC/DI、Bean 生命周期、`@Configuration`、AOP 代理（JDK/CGLIB）、声明式事务
   → 这是所有东西的地基，跳过它后面全是死记硬背。

2. **Spring MVC**：把 `doDispatch()` 九步流程跑通
   → 动手写一个自定义 `HandlerMethodArgumentResolver` 和一个 `HandlerInterceptor`。

3. **Spring Boot**：重点搞懂自动配置
   → 自己写一个 starter + `AutoConfiguration.imports` 完整体验一遍；理解条件注解与配置优先级。

4. **Spring Cloud**：先做「注册中心 + OpenFeign 调用」最小闭环
   → 再加 Gateway，最后补熔断与链路追踪。

### 推荐实践清单

- [ ] 用 `/actuator/conditions` 查看某个自动配置为什么没生效
- [ ] 自定义一个 starter（含 `@ConfigurationProperties` + 自动配置类 + imports 文件）
- [ ] 用 `@ControllerAdvice` 实现统一响应包装与全局异常处理
- [ ] 写一个 `HandlerInterceptor` 实现接口耗时统计与 traceId 透传
- [ ] 搭建 Nacos + 两个 Boot 服务，用 OpenFeign 完成一次调用
