# SB6 · 测试体系（切片测试 / MockMvc / Testcontainers）

> **一课目标**：摆脱"只剩 @SpringBootTest 一锅炖"——按测试金字塔选类型，切片测试提速，
> MockMvc 验 Web 层，Testcontainers 验真实依赖。
> **前置**：场景⑫（测试体系与 CI 初步）、SB4/SB5
> **本课实测**：手写 `@WebMvcTest` 切片测试并通过（2 个用例 / 6.035 秒）

---

## 目录

- [一、测试金字塔（什么测什么）](#sb6-1)
- [二、@SpringBootTest 全量上下文（慢，但真实）](#sb6-2)
- [三、切片测试四类（@WebMvcTest / @DataJpaTest / @JsonTest / @RestClientTest）](#sb6-3)
- [四、MockMvc 三件套（perform / andExpect / andDo）](#sb6-4)
- [五、Mock 注解：@MockBean → @MockitoBean（Boot 3.4+）](#sb6-5)
- [六、Testcontainers（真实 PG/Redis/Kafka）](#sb6-6)
- [七、本项目测试现状对照](#sb6-7)
- [八、本课实测（切片测试 2/2 通过）](#sb6-8)
- [九、自测题（答案）](#sb6-9)
- [十、一句话](#sb6-10)

---

<a id="sb6-1"></a>
## 一、测试金字塔（什么测什么）

```text
        E2E（少量）
       ┌─────────┐      真实环境全链路（最慢、最脆、最贵）
      ┌───────────┐
     │  集成测试   │      @SpringBootTest（全上下文）+ 真实/容器依赖
    ┌───────────────┐
   │    切片测试     │    @WebMvcTest / @DataJpaTest（只加载一层）
  ┌───────────────────┐
 │      单元测试       │  纯逻辑（不启动 Spring，毫秒级）
 └───────────────────────┘

原则：
  · 底层多、顶层少（单元测试几百个，E2E 几个）
  · 越往下越快、越稳；越往上越真实、越慢
  · 什么值得测：核心业务逻辑（Service）、边界（校验/异常）、关键 SQL/缓存行为
```

**本项目 7 个测试文件的类型对照**（`backend/src/test/java`）：

| 文件 | 类型 | 说明 |
|---|---|---|
| `ResearchTaskControllerTest` | 切片（MockMvc + Mock） | Web 层 |
| `ResearchTaskOrchestratorTest` | 单元 | 核心编排逻辑 |
| `CaffeineBasicsTest` | 单元 | 缓存 API 学习 |
| `MultiLevelCacheTest` | 单元/集成 | 多级缓存 |
| `RepositoryPersistenceTest` | 集成（Spring 上下文） | 持久化映射 |
| `JpaTaskEventLogStoreIntegrationTest` | 集成 | JPA 存储 |
| `MyBatisTaskEventLogStoreIntegrationTest` | 集成 | MyBatis 存储 |

<a id="sb6-2"></a>
## 二、@SpringBootTest 全量上下文（慢，但真实）

```java
@SpringBootTest                       // 默认 MOCK 环境（不启真实服务器）
class ResearchAgentApplicationTests {
    @Test
    void contextLoads() { }           // 冒烟测试：能启动就算过
}
```

**`webEnvironment` 四种**：

| 值 | 启动真实 Tomcat | 说明 |
|---|---|---|
| `MOCK`（默认） | ❌ | 只造 Web 上下文（MockMvc 可用） |
| `RANDOM_PORT` | ✅（随机端口） | 可用 TestRestTemplate/WebTestClient 发真实 HTTP |
| `DEFINED_PORT` | ✅（配置端口） | 会占 8080（测试并行时冲突） |
| `NONE` | ❌ | 完全非 Web 上下文 |

```text
★ 慢的原因：启动完整 Spring 容器（所有自动配置 + 所有 Bean）
  项目里 20+ 秒（H2 + MyBatis + JPA + Kafka 自动配置都装配）
★ 什么时候必须用它：
  ① 多组件联动（事务 + AOP + 持久化 + 缓存）
  ② 需要真实 HTTP（RANDOM_PORT）
  ③ 冒烟测试（contextLoads）
★ 提速：
  ① 多测试共享上下文（相同配置的 @SpringBootTest 会复用 ApplicationContext）
  ② 加 @DirtiesContext 慎用（强制重建 = 变慢）
  ③ 能用切片就不用全量
```

<a id="sb6-3"></a>
## 三、切片测试四类（只加载一部分）

| 注解 | 加载什么 | 不加载什么 | 典型场景 |
|---|---|---|---|
| `@WebMvcTest` | Web 层（Controller/Filter/Advice/MockMvc） | Service/Repository（用 Mock） | 接口路径/参数/JSON/状态码 |
| `@DataJpaTest` | JPA（Entity/Repository + 内嵌库） | Service/Controller | 实体映射/SQL/事务 |
| `@JsonTest` | Jackson 序列化上下文 | 其他 | DTO 的 JSON 契约 |
| `@RestClientTest` | RestTemplate/WebClient | 其他 | 调外部 API 的客户端 |

**切片测试的核心收益**：

```text
@SpringBootTest 全量：约 20 秒
@WebMvcTest 切片    ：约 2~6 秒（只装 Web 层）
→ 数百个测试的 CI，从"十几分钟"降到"几分钟"

★ 代价：切片里没真实的 Service/Repository → 需要 Mock（@MockitoBean）
```

<a id="sb6-4"></a>
## 四、MockMvc 三件套（perform / andExpect / andDo）

```java
// ① perform：构造请求并发起（不占端口，直接调 DispatcherServlet）
mockMvc.perform(get("/api/demo/greeting")
                .param("name", "Kafka")
                .header("X-Trace-Id", "trace-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"a-1\",\"requestType\":\"CREATE\",\"desc\":\"x\"}"))
// ② andExpect：断言（状态码 / JSON / 头 / 内容）
        .andExpect(status().isOk())
        .andExpect(content().string("Hello, Kafka!"))
        .andExpect(jsonPath("$.requestId").value("a-1"))
        .andExpect(jsonPath("$.fields", hasSize(2)))
        .andExpect(header().exists("X-Trace-Id"))
// ③ andDo：打印/记录（排障时用）
        .andDo(print());
```

**常用断言速查**：

```text
status().isOk() / is4xxClientError() / is5xxServerError()
jsonPath("$.a.b").value("x") / exists() / doesNotExist() / isEmpty()
content().string("...") / json("{...}") / contentType(APPLICATION_JSON)
header().string("Location", "...")
```

<a id="sb6-5"></a>
## 五、Mock 注解：@MockBean → @MockitoBean（Boot 3.4+）

```java
// Boot 3.3 及以前
@MockBean private DemoGreetingService service;
@SpyBean private RealService realService;

// ★ Boot 3.4+（本项目 3.5.13）：@MockBean 弃用，改用
@MockitoBean private DemoGreetingService service;
@MockitoSpyBean private RealService realService;
```

```text
为什么改：@MockBean 是 Spring Boot 自己的实现（与 Mockito 深度耦合），
         3.4 起改用 Spring Framework 6.2 的 @MockitoBean（标准化、支持更多容器配置）

★ 迁移注意：
  ① 依赖 spring-boot-starter-test（含 Mockito）
  ② @MockitoBean 支持字段/方法级
  ③ 老项目里 @MockBean 仍能用（只是 deprecation 警告）——新代码用 @MockitoBean
```

<a id="sb6-6"></a>
## 六、Testcontainers（真实 PG/Redis/Kafka）

```xml
<!-- pom.xml 加（本项目未加，作为生产化建议） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@SpringBootTest
@Testcontainers
class RepositoryIntegrationTest {

    @Container
    @ServiceConnection            // Boot 3.1+ 自动把容器地址注入数据源
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void shouldPersist() { ... }
}
```

```text
★ @ServiceConnection 的价值：不用手写 @DynamicPropertySource 映射容器端口
  支持：PostgreSQL/MySQL/Redis/Kafka/MongoDB/RabbitMQ...

★ 适用场景：
  ① SQL 方言/索引/唯一约束等"内嵌库测不出来"的行为（H2 与 PG 有差异）
  ② 缓存/消息的真实协议（Redis 的 Lua、Kafka 的分区）
★ 代价：需要 Docker（CI 要有 Docker 或 Testcontainers Cloud）→ 比切片慢、比全量依赖真
★ 本项目现状：用 H2 内嵌（快，但部分 SQL 行为与 PG 有差异）→ 生产化再加 PG 容器
```

<a id="sb6-7"></a>
## 七、本项目测试现状对照

```text
已有 7 个测试（详见第一节表）：
  · 1 个 Web 切片（ResearchTaskControllerTest，MockMvc + Mock）
  · 1 个单元（ResearchTaskOrchestratorTest，核心编排）
  · 2 个缓存相关（CaffeineBasicsTest / MultiLevelCacheTest）
  · 3 个持久化集成（RepositoryPersistenceTest / Jpa&MyBatis EventLogStore）

缺口（生产化建议）：
  □ JSON 契约测试（@JsonTest）：DTO 字段改名/类型变更的回归保护
  □ Testcontainers（PG）：验证真实 SQL 方言/索引
  □ Kafka 消费幂等测试（EmbeddedKafka 或 Testcontainers）
  □ Actuator/健康端点测试（@WebMvcTest 覆盖 /actuator/health）
```

<a id="sb6-8"></a>
## 八、本课实测（切片测试 2/2 通过）

### 8.1 测试代码（`DemoGreetingControllerTest`）

```java
@WebMvcTest(controllers = DemoGreetingController.class)   // 只装配这一个 Controller
class DemoGreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoGreetingService demoGreetingService;       // Service 用 Mock（Boot 3.4+ 注解）

    @Test
    void greetingShouldReturnServiceResult() throws Exception {
        when(demoGreetingService.greet("Kafka")).thenReturn("Hello, Kafka!");

        mockMvc.perform(get("/api/demo/greeting").param("name", "Kafka"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Kafka!"));
    }

    @Test
    void greetingShouldUseDefaultParameter() throws Exception {
        when(demoGreetingService.greet("world")).thenReturn("Hello, world!");

        mockMvc.perform(get("/api/demo/greeting"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, world!"));
    }
}
```

### 8.2 运行结果（2026-11-26）

```powershell
mvn -q test -Dtest=DemoGreetingControllerTest
```

```text
Test set: ...DemoGreetingControllerTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.035 s
BUILD SUCCESS
```

```text
★ 三个关键点：
  ① controllers = DemoGreetingController.class → 不扫全项目（切片的关键）
  ② @MockitoBean（Boot 3.4+ 新注解）→ Service 用 Mock 替身
  ③ 6 秒完成（vs @SpringBootTest 全量 20+ 秒）
★ 注意 ObjectProvider 注入：Controller 里的 ObjectProvider<DemoGreetingService>
  在切片里能拿到 Mock 实例（Spring 自动包装）
```

<a id="sb6-9"></a>
## 九、自测题（答案）

### Q1：什么情况下必须用 @SpringBootTest 而不是切片？

```text
① 多组件联动（事务 + AOP + 持久化 + 缓存一起验证）
② 需要真实 HTTP（webEnvironment=RANDOM_PORT）
③ 冒烟测试（contextLoads）
④ 验证自动配置的效果（切片会裁剪自动配置）
```

### Q2：@WebMvcTest 为什么 Controller 能启动但 Service 拿不到？怎么解决？

```text
@WebMvcTest 只装配 Web 层（Controller/Filter/Advice/MockMvc）
Service/Repository 不在装配范围 → 需 @MockitoBean 提供替身
（否则启动报 NoSuchBeanDefinitionException）
```

### Q3：@MockBean 和 @MockitoBean 的区别？

```text
@MockBean：Boot 3.3 及以前的注解（Spring Boot 自己实现，已弃用）
@MockitoBean：Boot 3.4+ 改用 Spring Framework 6.2 的标准化注解
→ 新代码用 @MockitoBean；老代码 @MockBean 仍可用（deprecation 警告）
```

### Q4：Testcontainers 与 H2 内嵌库各适合什么？

```text
H2（内嵌）：快、无 Docker 依赖；但 SQL 方言/索引/锁行为与真实库有差异
Testcontainers（容器）：真实 PG/Redis/Kafka；慢一点、需 Docker
→ 单元/切片用 H2；"依赖真实语义"的集成测试用 Testcontainers
```

### Q5：为什么 @DirtiesContext 要慎用？

```text
它标记"测试后销毁上下文"→ 下一个测试重新启动 Spring（20+ 秒）
  → 滥用会让测试套件从 2 分钟变 20 分钟
替代：用 @Transactional 回滚数据、@Sql 清理、独立测试库
```

<a id="sb6-10"></a>
## 十、一句话

```text
测试金字塔：单元多、切片主力、集成兜底、E2E 极少。
切片测试的价值 = 20 秒 → 6 秒（@WebMvcTest 只装 Web 层 + @MockitoBean 替身）。
真实依赖语义（SQL 方言/缓存协议）交给 Testcontainers。
```

