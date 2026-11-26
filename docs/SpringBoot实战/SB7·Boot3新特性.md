# SB7 · Spring Boot 3 新特性（虚拟线程 / Problem Details / AOT / Jakarta）

> **一课目标**：把项目正在用的 Boot 3.5.13 的新能力摸一遍——**虚拟线程**（重点实测）、
> Problem Details、AOT/Native、Jakarta 迁移要点。
> **前置**：SB4（容器部署）、SB5（可观测）
> **本课实测**：虚拟线程开关前后对比 + Problem Details 响应格式（真实启动验证）

---

## 目录

- [一、Boot 3 全景（相对 2.x 的六件事）](#sb7-1)
- [二、Jakarta 迁移（javax.* → jakarta.*）](#sb7-2)
- [三、★ 虚拟线程（原理 + 开关 + 实测 + 坑）](#sb7-3)
- [四、AOT 与 GraalVM Native Image](#sb7-4)
- [五、★ Problem Details（RFC 9457 错误响应）](#sb7-5)
- [六、其他新能力（RestClient / @HttpExchange / Observation）](#sb7-6)
- [七、本项目实测数据（两张对比表）](#sb7-7)
- [八、自测题（答案）](#sb7-8)
- [九、一句话](#sb7-9)

---

<a id="sb7-1"></a>
## 一、Boot 3 全景（相对 2.x 的六件事）

| 变化 | 内容 | 项目影响 |
|---|---|---|
| **Java 基线 17+** | Boot 3 要求 JDK 17+（项目 JDK 21） | 已满足 |
| **Jakarta EE 9+** | `javax.*` → `jakarta.*`（Servlet/Validation/JPA/Persistence） | 项目已是 3.5.13（无迁移成本） |
| **虚拟线程** | Boot 3.2+ `spring.threads.virtual.enabled=true` | 本课实测（默认未开） |
| **AOT / Native** | `spring-boot-maven-plugin` 的 AOT + GraalVM Native | 未用（可后续尝试） |
| **Problem Details** | RFC 9457 标准错误体（`application/problem+json`） | 本课实测（默认关） |
| **Observability** | Micrometer 1.10+ Observation API + Tracing | 项目只用了 metrics（SB5） |

<a id="sb7-2"></a>
## 二、Jakarta 迁移（javax.* → jakarta.*）

**为什么迁**：Oracle 把 Java EE 捐给 Eclipse 基金会 → 改名 Jakarta EE → 包名从 `javax.*` 改为 `jakarta.*`（**字节码不兼容**）。

**从 Boot 2.x 升级要改的地方**：

```text
① 依赖坐标：javax.servlet-api → jakarta.servlet-api
           javax.persistence-api → jakarta.persistence-api
           javax.validation-api → jakarta.validation-api
② import：import javax.servlet.http.HttpServletRequest;
          → import jakarta.servlet.http.HttpServletRequest;
③ 第三方库版本（必须支持 Jakarta）：
          Tomcat 10+ / Jetty 11+ / Hibernate 6+ / Spring 6+
④ 配置类名：部分属性/类名变化（Boot 3 的 migration guide 列了清单）
```

**本项目**：

```text
直接基于 Boot 3.5.13 开发 → 所有 import 都是 jakarta.*（零迁移成本）
检查命令：grep -r "javax\." backend/src/main/java  → 应该没有（除 javax.annotation 极少数）
★ 教训：新项目直接上 Boot 3，别在 2.x 上攒技术债
```

<a id="sb7-3"></a>
## 三、★ 虚拟线程（原理 + 开关 + 实测 + 坑）

### 3.1 原理：为什么快

```text
平台线程（传统）：
  1 个请求 = 1 个 OS 线程（Tomcat 200 线程 = 200 个 OS 线程）
  IO 阻塞（DB/HTTP/LLM 调用）时 → 线程被挂起 → 白白占着内存（1MB 栈）
  → 高并发时线程数成为瓶颈（Thread per Request 模型的死穴）

虚拟线程（JDK 21 正式）：
  1 个请求 = 1 个虚拟线程（JVM 调度，不是 OS 线程）
  阻塞时自动"卸载"（unmount）→ 载体线程（carrier）去跑别的虚拟线程
  → 单个 OS 线程可承载成千上万个"同时在等 IO"的请求
  → 内存：虚拟线程栈小（KB 级，按需增长）

★ 本质：把"阻塞等待"的代价从"线程占用"降低到"对象占用"
```

### 3.2 开启方式（一行配置）

```yaml
spring:
  threads:
    virtual:
      enabled: true     # Boot 3.2+：Tomcat/定时任务/@Async 都用虚拟线程执行
```

```java
// 或代码方式（自定义线程池）
@Bean
public AsyncTaskExecutor applicationTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
}
```

### 3.3 本课实测（项目真实数据）

**实验端点**（`/api/demo/thread` 返回当前线程）：

```java
@GetMapping("/thread")
public String thread() {
    Thread t = Thread.currentThread();
    return "name=" + t.getName() + " | isVirtual=" + t.isVirtual() + " | class=" + t.getClass().getSimpleName();
}
```

**对比结果（2026-11-26）**：

| | 未开启（默认） | 开启 `spring.threads.virtual.enabled=true` |
|---|---|---|
| 线程名 | `http-nio-8080-exec-1` | **`tomcat-handler-0`** |
| isVirtual | `false` | **`true`** |
| 线程类 | `TaskThread`（平台线程） | **`VirtualThread`** |

```text
★ 结论：一开就生效（Tomcat 的请求处理线程全部换成虚拟线程）
```

### 3.4 虚拟线程的四个坑（必须知道）

```text
① synchronized 块会"钉住"载体线程（pinning）
   虚拟线程在 synchronized 里阻塞时，载体线程无法被复用 → 退化为平台线程
   → JDK 24 才解决（JEP 491）；当前用 ReentrantLock 替代 synchronized
   → 检查：-Djdk.tracePinnedThreads=full 启动，看日志里的 pinning 报告

② ThreadLocal 爆炸
   每个虚拟线程一份 ThreadLocal（原来线程数少，现在可能百万级）
   → 大对象放 ThreadLocal 会内存爆炸；用 ScopedValue（JDK 21 预览）或减少 ThreadLocal

③ 数据库连接池仍是瓶颈
   虚拟线程让"线程"不再是瓶颈 → 但连接池（Hikari 默认 10）还是
   → 高并发下要调大连接池（注意别超过 DB 的 max_connections）

④ 阻塞型原生调用（JNI/some JDBC）
   底层 native 调用阻塞时，虚拟线程无法卸载（pinning）→ 需驱动适配
```

### 3.5 本项目是否该开？

```text
本项目的 IO 特征：
  · LLM 调用（几分钟，IO 等待）★ 最适合虚拟线程
  · DB 读写（毫秒级）
  · Kafka 消费（已有独立线程）
→ 理论上适合开（尤其是 LLM 长等待场景）
→ 但当前架构已用"Kafka 消费者 + 异步"绕过（任务执行不在 Web 线程）
  → 开虚拟线程的收益主要在"Web 层高并发请求"
  → 建议：线上压测对比后再开（不要盲目开）

★ 重要：虚拟线程不是"更快"，而是"同样的线程数能干更多等待中的活"
  纯 CPU 密集场景没有收益（甚至略差）
```

<a id="sb7-4"></a>
## 四、AOT 与 GraalVM Native Image

```text
① AOT（Ahead-of-Time）处理：构建期生成 bean 定义代码/反射配置
   mvn spring-boot:process-aot  → 生成 target/spring-aot/（启动更快、反射减少）

② GraalVM Native Image：编译成原生可执行文件
   mvn -Pnative native:compile  → 启动 ~100ms、内存更低（无 JVM）
   或 mvn spring-boot:build-image（构建容器镜像）

③ Native 的限制（重要）：
   · 反射/动态代理要显式声明（@RegisterReflectionForBinding 等）
   · 部分库不支持（动态生成代理的、JIT 依赖的）
   · 构建慢（分钟级）、镜像大（包含 native 工具链时）
   · 调试困难（不是 JVM）

④ 何时值得：
   冷启动敏感的 Serverless / 高密度部署 / 内存受限环境
   → 本项目（单体 + 长运行）收益不明显 → 暂不引入
```

<a id="sb7-5"></a>
## 五、★ Problem Details（RFC 9457 错误响应）

### 5.1 是什么

```text
RFC 9457（原 7807）= 标准的 HTTP 错误响应格式（application/problem+json）：

{
  "type": "about:blank",          // 错误类型 URI（默认 about:blank）
  "title": "Not Found",           // 简短标题
  "status": 404,                  // HTTP 状态码
  "detail": "No static resource api/not-exist.",   // 详细说明
  "instance": "/api/not-exist"    // 出错的请求路径
}

★ 好处：错误响应标准化（前端/网关/监控都能统一处理），不用自定义错误 DTO
```

### 5.2 开启

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

**本课实测（项目真实数据）**：

| | 未开启 | 开启 `spring.mvc.problemdetails.enabled=true` |
|---|---|---|
| Content-Type | `application/json` | **`application/problem+json`** |
| 响应体 | 项目的统一错误格式（code/message/path） | **RFC 9457 标准格式** |

```text
开启后 curl /api/not-exist 的真实输出：
HTTP/1.1 404
Content-Type: application/problem+json

{"type":"about:blank","title":"Not Found","status":404,
 "detail":"No static resource api/not-exist.","instance":"/api/not-exist"}
```

### 5.3 与项目现有全局异常处理的冲突（重要）

```text
项目已有 GlobalExceptionHandler（@RestControllerAdvice）→ 自己定义了统一错误格式
（code/message/path/fields/retryable，本项目的 ApiErrorResponse）

★ 两者关系：
  ① @RestControllerAdvice 显式处理的异常 → 走项目的格式（Problem Details 不生效）
  ② 未被 Advice 覆盖的（404/405/类型转换失败等）→ 走 Problem Details（如果开启）
→ 所以"开启 Problem Details"不等于"全局换格式"，只影响"没人管"的异常

建议（本项目）：
  如果前端/网关依赖项目的统一格式 → 保持 problemdetails 关闭（避免两套格式）
  如果要做标准 API → 把 GlobalExceptionHandler 逐步迁移成 ProblemDetail 返回
```

<a id="sb7-6"></a>
## 六、其他新能力（RestClient / @HttpExchange / Observation）

```text
① RestClient（Boot 3.2+）：RestTemplate 的现代化替代（同步、流畅 API）
   RestClient.create().get().uri(url).retrieve().body(Class)

② @HttpExchange（Spring 6.1）：声明式 HTTP 客户端接口（像 Feign）
   @HttpExchange("/api") interface UserClient { @GetExchange("/users/{id}") User get(@PathVariable long id); }
   → 配 RestClient/WebClient 适配器

③ Observation API（Micrometer 1.10+）：统一"指标 + 追踪"的门面
   Observation.createNotStarted("task.execute", registry).observe(() -> doWork())
   → 一次打点，同时出 metrics 和 tracing

④ Micrometer Tracing（Boot 3）：桥接 Brave/OpenTelemetry
   → 分布式链路追踪（traceId/spanId 自动进 MDC → 日志可关联）
```

```text
本项目现状：
  · 用 RestTemplate/HttpClient（未迁 RestClient）
  · 无 tracing（只有 metrics）→ 可观测三件套还差"追踪"
  · 日志有 traceId（自己实现的 RequestTraceFilter 注入 MDC？——项目有 RequestTraceFilter）
```

<a id="sb7-7"></a>
## 七、本项目实测数据（2026-11-26）

### 7.1 虚拟线程对比

| 项 | 默认（未开） | 开启后 |
|---|---|---|
| `GET /api/demo/thread` | `name=http-nio-8080-exec-1 \| isVirtual=false \| class=TaskThread` | `name=tomcat-handler-0 \| isVirtual=true \| class=VirtualThread` |
| 结论 | Tomcat 请求跑在平台线程 | **Tomcat 请求跑在虚拟线程**（一行配置生效） |

### 7.2 Problem Details 对比

| 项 | 默认（未开） | 开启后 |
|---|---|---|
| 404 的 Content-Type | `application/json` | **`application/problem+json`** |
| 响应体 | 项目统一格式 | `{"type":"about:blank","title":"Not Found","status":404,"detail":"No static resource api/not-exist.","instance":"/api/not-exist"}` |

### 7.3 项目落地建议

```text
① 虚拟线程：先压测再决定（收益在 Web 高并发；纯 CPU 无收益）
   若开：先把 synchronized 换 ReentrantLock、评估 ThreadLocal、调大连接池
② Problem Details：与现有 GlobalExceptionHandler 二选一（避免两套格式）
③ AOT/Native：暂不引入（单站长运行收益低）
④ 下一步：加 Micrometer Tracing（traceId/spanId 进日志）补齐"追踪"这条腿
```

<a id="sb7-8"></a>
## 八、自测题（答案）

### Q1：虚拟线程为什么能提升吞吐？对 CPU 密集场景有用吗？

```text
提升原因：阻塞时自动卸载（载体线程复用）→ 少量 OS 线程承载大量"等待中"的请求
CPU 密集场景：没有阻塞可卸载 → 收益≈0（甚至略差，因调度开销）
→ 虚拟线程 = 为 IO 密集/高并发等待设计
```

### Q2：synchronized 与虚拟线程有什么冲突？

```text
虚拟线程在 synchronized 块内阻塞时，载体线程被"钉住"（pinning）→ 无法复用
→ 退化为平台线程（失去虚拟线程的意义）
JDK 24 之前：用 ReentrantLock 替代 synchronized
检测：-Djdk.tracePinnedThreads=full
```

### Q3：开了 Problem Details，项目的 GlobalExceptionHandler 会怎样？

```text
不冲突（分层）：
  ① @RestControllerAdvice 显式处理的异常 → 走项目的统一格式
  ② 未被处理的（404/405/参数绑定失败）→ 走 Problem Details
→ 想全站标准格式：把 Advice 改为返回 ProblemDetail（逐步迁移）
```

### Q4：Jakarta 迁移最关键的三件事？

```text
① 依赖换（javax.*-api → jakarta.*-api）
② import 换（javax.servlet → jakarta.servlet）
③ 第三方库必须支持 Jakarta（Tomcat 10+/Hibernate 6+/Spring 6）
（本项基于 Boot 3 新建 → 零迁移成本）
```

### Q5：AOT/Native 的收益与代价？

```text
收益：启动快（~100ms）、内存低、无 JVM
代价：构建慢、反射/动态代理受限、部分库不兼容、调试难
→ 适合 Serverless/冷启动敏感；本项目（长运行单体）收益低
```

<a id="sb7-9"></a>
## 九、一句话

```text
Boot 3 = Jakarta 换包名 + 虚拟线程（一开就生效）+ Problem Details（标准错误体）+ AOT/Native。
虚拟线程不是"更快"，是"同样线程数干更多等待活"——纯 CPU 无收益，还要防 pinning。
本课两个开关都实测过：isVirtual false→true、Content-Type json→problem+json。
```
