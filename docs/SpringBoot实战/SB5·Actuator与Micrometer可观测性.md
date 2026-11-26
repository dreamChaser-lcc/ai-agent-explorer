# SB5 · Actuator 与 Micrometer 可观测性（端点 / 自定义指标 / Prometheus）

> **一课目标**：把应用变成"可观测"——健康怎么验、指标怎么出、Prometheus/Grafana 怎么接。
> **前置**：SP7（Actuator 初识）、SB4（部署与探针）
> **本项目现状**：已引 actuator，但只暴露 `health,info,env`（yml L105-111）；**未引 Prometheus 注册表**
> **本课动作**：暴露 metrics 端点 + 手写自定义指标 + 实测查看

---

## 目录

- [一、Actuator 是什么（端点/暴露/端口分离）](#sb5-1)
- [二、端点全解（18 个常用端点）](#sb5-2)
- [三、暴露策略与安全（include / 独立端口 / Security）](#sb5-3)
- [四、Micrometer（门面 + 四类指标）](#sb5-4)
- [五、Prometheus + Grafana 接入](#sb5-5)
- [六、健康检查（HealthIndicator + 探针分组）](#sb5-6)
- [七、本项目改造与实测](#sb5-7)
- [八、自测题（答案）](#sb5-8)
- [九、一句话](#sb5-9)

---

<a id="sb5-1"></a>
## 一、Actuator 是什么（端点/暴露/端口分离）

```text
Actuator = Spring Boot 的"仪表盘"：
  引入 spring-boot-starter-actuator → 自动装配一组 HTTP 端点（/actuator/**）
  端点 = 面向运维/排障的只读（或少量写）接口

三类端点：
  ① 内置端点（health/info/metrics/env/beans/loggers/threaddump...）
  ② 扩展端点（第三方 starter 注册的，如 /actuator/prometheus）
  ③ 自定义端点（@Endpoint 自己写）

★ 两个开关概念（千万别混）：
  enabled   ：端点是否被创建（管理端点存在性）
  exposure  ：端点是否通过 HTTP 暴露（能不能访问）
  → 默认只暴露 health（Web）；其他要 include 才可见
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # 暴露哪些（* 表示全开，生产慎用）
        exclude: env,beans                        # 排除哪些
      base-path: /actuator                        # 默认前缀
  endpoint:
    health:
      show-details: always                        # health 是否显示明细（生产用 when-authorized）
```

**端口分离（生产推荐）**：

```yaml
management:
  server:
    port: 8081            # 管理端点走独立端口（与业务端口隔离）
    address: 127.0.0.1    # 只允许本机（配合防火墙/内网）
  endpoints:
    web:
      base-path: /manage  # 独立前缀
```

```text
★ 为什么要分离：
  ① 安全（管理端点不对公网暴露，只给内网/监控系统）
  ② 流量隔离（监控抓取不打到业务端口）
  ③ 权限（可以做不同的访问控制）
```

<a id="sb5-2"></a>
## 二、端点全解（18 个常用端点）

| 端点 | 作用 | 生产建议 |
|---|---|---|
| `/actuator/health` | 健康检查（聚合所有 HealthIndicator） | ✅ 必开（探针用） |
| `/actuator/info` | 应用信息（版本/构建/git） | ✅ 开 |
| `/actuator/metrics` | 指标列表 + 单指标查询 | ✅ 开（或走 Prometheus） |
| `/actuator/metrics/{name}` | 单指标值（含 tags 维度） | ✅ |
| `/actuator/prometheus` | Prometheus 格式（需 micrometer-registry-prometheus） | ✅ 监控用 |
| `/actuator/env` | 配置来源与取值（**可能泄漏密钥**） | ⚠ 生产禁开 |
| `/actuator/configprops` | @ConfigurationProperties 绑定值 | ⚠ 谨慎 |
| `/actuator/beans` | 容器所有 Bean（排障神器，量大） | ⚠ 谨慎 |
| `/actuator/conditions` | 自动配置条件报告（= --debug 的 JSON 版） | ⚠ 谨慎（学习有用） |
| `/actuator/mappings` | 所有 URL 映射 | ⚠ 谨慎 |
| `/actuator/loggers` | 查看/动态修改日志级别（**POST 可改**） | ⚠ 需权限 |
| `/actuator/threaddump` | 线程栈（排死锁/卡顿） | ⚠ 谨慎 |
| `/actuator/heapdump` | 堆转储（下载 .hprof） | ⚠ 生产禁开（内存大） |
| `/actuator/metrics/jvm.memory.used` | JVM 内存 | ✅ |
| `/actuator/scheduledtasks` | 定时任务列表 | ✅ 开 |
| `/actuator/startup` | 启动步骤耗时（需配置） | 可选 |
| `/actuator/caches` | 缓存信息（清缓存用） | 可选 |
| `/actuator/refresh` | 刷新配置（Spring Cloud Context） | 视情况 |

<a id="sb5-3"></a>
## 三、暴露策略与安全（include / 独立端口 / Security）

```yaml
# ① 学习/开发：全开（方便排查）
management.endpoints.web.exposure.include: "*"

# ② 生产：只开监控必需的
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  server:
    port: 8081
    address: 127.0.0.1        # 只本机/内网
```

**加 Spring Security 后**：

```java
// 让 health 匿名可访问，其余需认证（K8s 探针能通，运维走认证）
http.authorizeHttpRequests(a -> a
        .requestMatchers("/actuator/health/**").permitAll()
        .requestMatchers("/actuator/**").hasRole("OPS")
        .anyRequest().permitAll());
```

```text
★ 三个"绝对不能开"的（生产）：heapdump（能下全量内存）、env（能看密钥）、
  loggers（能改日志级别把敏感日志打出来）
```

<a id="sb5-4"></a>
## 四、Micrometer（门面 + 四类指标）

```text
Micrometer = 指标门面（SLF4J 的指标版）：
  你只对 MeterRegistry 打点 → 后端（Prometheus/Datadog/...）由注册表实现
  → 换监控系统不改业务代码

四类核心指标：
  Counter          只增不减（请求数/错误数/任务提交数）
  Gauge            瞬时值（当前活跃任务数/队列长度/连接数）
  Timer            耗时分布（请求耗时/任务执行耗时）→ 同时出 count/total/max
  DistributionSummary  数值分布（响应大小）
```

**打点示例（本项目实验）**：

```java
@Service
public class DemoMetricsService {

    private final Counter hits;
    private final AtomicInteger active = new AtomicInteger();

    public DemoMetricsService(MeterRegistry registry) {
        this.hits = Counter.builder("demo.hits")
                .description("Demo 接口被调用次数")
                .tag("module", "experiment")           // 标签（维度）→ Prometheus label
                .register(registry);
        registry.gauge("demo.active", active);         // Gauge 绑到 AtomicInteger
    }

    public void hit() {
        hits.increment();
        active.incrementAndGet();                      // 模拟"当前活跃"
    }
}
```

```text
★ 标签（tag）是关键：
  demo.hits{module="experiment"}  → 可以按 module 维度聚合/过滤
  但标签基数别太高（别用 taskId/用户ID 当 tag → 指标爆炸）

★ 命名规范（Prometheus）：小写 + 点分（demo.hits → demo_hits_total）
```

**Spring Boot 自动打点的指标（不用自己写就有）**：

```text
http.server.requests        HTTP 请求（count/sum/max + uri/status/exception 标签）
jvm.memory.used/max         JVM 内存（各内存区）
jvm.gc.pause                GC 暂停（次数/耗时）
jvm.threads.live            JVM 线程数
process.cpu.usage           进程 CPU
system.cpu.usage            系统 CPU
hikaricp.connections.*      连接池（active/idle/pending）
tomcat.threads.*            Tomcat 线程池
```

<a id="sb5-5"></a>
## 五、Prometheus + Grafana 接入

**① 加依赖（本项目没加）**：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**② 暴露端点**：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

**③ Prometheus 抓取配置**：

```yaml
# prometheus.yml
scrape_configs:
  - job_name: research-agent-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8080"]   # 或容器服务名
    scrape_interval: 15s
```

**④ 常用 PromQL（Grafana 面板）**：

```text
# QPS
rate(http_server_requests_seconds_count[1m])
# P99 延迟
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
# 错误率
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
# JVM 堆使用
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
# GC 暂停
rate(jvm_gc_pause_seconds_sum[5m])
```

```text
★ 指标类型与后缀（Prometheus 格式）：
  Counter → xxx_total（累计值）
  Timer   → xxx_seconds_count / _sum / _bucket（直方图）
  Gauge   → xxx（瞬时值）
```

<a id="sb5-6"></a>
## 六、健康检查（HealthIndicator + 探针分组）

```java
// 自定义健康检查（如"LLM 服务可达性"）
@Component
public class LlmHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        boolean ok = checkLlmReachable();        // 你的探测逻辑
        return ok ? Health.up().withDetail("llm", "reachable").build()
                  : Health.down().withDetail("llm", "unreachable").build();
    }
}
```

```text
★ HealthIndicator 的聚合：/actuator/health 是聚合结果（所有 Indicator 都 UP 才 UP）
★ order/分组：可以给 Indicator 分组（如 liveness 组只放"进程活着"的检查）
  避免把"数据库挂了"算进 liveness（否则 k8s 会重启容器，越重启越糟）
```

**探针分组（K8s 用）**：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true          # 暴露 /actuator/health/liveness 和 /readiness
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

```text
★ 关键区分：
  liveness  = 活着吗（失败 → 重启）→ 只放"进程级"检查（避免把下游依赖放进来）
  readiness = 能接流量吗（失败 → 摘流量）→ 放"依赖就绪"检查（DB/缓存连通）
```

<a id="sb5-7"></a>
## 七、本项目改造与实测

### 7.1 现状（改造前）

```yaml
# application.yml L105-111
management:
  endpoints:
    web:
      exposure:
        include: health,info,env      # ★ 只暴露 3 个，metrics 没开
```

**实测（改造前）**：

```powershell
curl.exe -s http://localhost:8080/actuator/health     # {"status":"UP"} ✅
curl.exe -s http://localhost:8080/actuator/info       # {}（没配 info）
curl.exe -s http://localhost:8080/actuator/env        # 配置明细（学习用）✅
curl.exe -s http://localhost:8080/actuator/metrics    # 404（未暴露）❌
```

### 7.2 改造（本课落地）

```yaml
management:
  endpoints:
    web:
      exposure:
        # 学习环境：加上 metrics（观察 JVM/HTTP 指标）
        include: health,info,env,metrics
  endpoint:
    health:
      show-details: always        # health 显示明细（开发用）
```

### 7.3 自定义指标实验（`infrastructure/experiment/metrics/`）

```java
// DemoMetricsService：Counter（调用次数）+ Gauge（当前活跃）
@Service
public class DemoMetricsService {
    private final Counter hits;
    private final AtomicInteger active = new AtomicInteger();

    public DemoMetricsService(MeterRegistry registry) {
        this.hits = Counter.builder("demo.hits")
                .description("Demo 接口被调用次数")
                .tag("module", "experiment")
                .register(registry);
        registry.gauge("demo.active", active);
    }

    public void hit() {
        hits.increment();
        active.incrementAndGet();
    }
}

// DemoMetricsController：/api/demo/metrics/hit 触发
@RestController
@RequestMapping("/api/demo/metrics")
public class DemoMetricsController {
    private final DemoMetricsService service;
    public DemoMetricsController(DemoMetricsService service) { this.service = service; }

    @GetMapping("/hit")
    public String hit() { service.hit(); return "hit"; }
}
```

### 7.4 实测（改造后，本课验证）

```powershell
# ① 指标列表里能看到自定义 + 内置
curl.exe -s http://localhost:8080/actuator/metrics
# → {"names":["demo.active","demo.hits","http.server.requests","jvm.memory.used",...]}

# ② 看自定义 Counter
curl.exe -s "http://localhost:8080/actuator/metrics/demo.hits"
# → {"name":"demo.hits","measurements":[{"statistic":"COUNT","value":3.0}],"availableTags":[{"tag":"module","values":["experiment"]}]}

# ③ 触发 3 次再查（值从 0 → 3）
curl.exe -s http://localhost:8080/api/demo/metrics/hit  (×3)
curl.exe -s "http://localhost:8080/actuator/metrics/demo.hits"

# ④ 内置指标：JVM 堆使用
curl.exe -s "http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap"

# ⑤ HTTP 请求指标（自带）
curl.exe -s "http://localhost:8080/actuator/metrics/http.server.requests"
```

### 7.4.1 实测结果（2026-11-26，lite 模式真实输出）

```text
① 启动：Started ResearchAgentApplication ✅
② GET /actuator/health
   {"status":"UP"}

③ GET /actuator/metrics（列表中筛出的关键指标）
   demo.active
   demo.hits
   http.server.requests
   http.server.requests.active
   jvm.memory.used
   process.cpu.time
   process.cpu.usage
   ★ 与改造前对比：metrics 端点从 404 → 可用（include 生效）

④ 触发 3 次 GET /api/demo/metrics/hit 后：
   GET /actuator/metrics/demo.hits
   {"name":"demo.hits","description":"Demo 接口被调用次数",
    "measurements":[{"statistic":"COUNT","value":3.0}],
    "availableTags":[{"tag":"module","values":["experiment"]}]}
   → Counter 值 = 3，tag 生效 ✅

⑤ GET /actuator/metrics/demo.active
   {"name":"demo.active","measurements":[{"statistic":"VALUE","value":3.0}]}
   → Gauge 值 = 3（AtomicInteger 绑定生效）✅

⑥ GET /actuator/metrics/jvm.memory.used?tag=area:heap
   {"name":"jvm.memory.used","baseUnit":"bytes",
    "measurements":[{"statistic":"VALUE","value":1.20495576E8}],
    "availableTags":[{"tag":"id","values":["G1 Survivor Space","G1 Old Gen","G1 Eden Space"]}]}
   → 堆使用约 115MB；G1 三个区都有数据 ✅

⑦ GET /actuator/metrics/http.server.requests
   {"name":"http.server.requests","baseUnit":"seconds",
    "measurements":[{"statistic":"COUNT","value":8.0},
                    {"statistic":"TOTAL_TIME","value":0.319795501},
                    {"statistic":"MAX","value":0.0620347}],
    "availableTags":[{"tag":"exception","values":["none"]},{"tag":"method","values":["GET"]},...]}
   → Boot 自带 HTTP 指标：8 次请求、总耗时 0.32s、最大 62ms ✅
```

**结论**：Micrometer 打点 → /actuator/metrics 可查（Counter/Gauge/内置指标三类全部验证通过）；
再加 micrometer-registry-prometheus + Grafana 即可出图。
### 7.5 生产化建议（本项目未做，列清单）

```text
□ 加 micrometer-registry-prometheus → /actuator/prometheus
□ management.server.port 分离（8081 只内网）
□ 关掉 env / heapdump / loggers（生产）
□ 自定义业务指标：任务提交数/成功数/失败数/执行耗时（Timer）
□ K8s 探针接 /actuator/health/liveness 与 /readiness
```

<a id="sb5-8"></a>
## 八、自测题（答案）

### Q1：`enabled` 与 `exposure` 有什么区别？

```text
enabled：端点对象是否被创建（管理层面）
exposure：端点是否通过 HTTP 暴露（访问层面）
→ 端点 enabled=true 但没 include → 访问会 404
→ 排查思路：先看 enabled，再看 exposure
```

### Q2：Counter、Gauge、Timer 分别适合什么？

```text
Counter：只增不减（请求数/错误数/任务提交数）
Gauge  ：瞬时值（活跃任务数/队列长度/连接数）→ 绑 AtomicInteger/Long
Timer  ：耗时分布（请求/任务耗时）→ 自动出 count/sum/max/分桶
（DistributionSummary：数值分布，如响应大小）
```

### Q3：为什么生产不能暴露 heapdump / env / loggers？

```text
heapdump：能下载完整堆内存（含密钥/用户数据）→ 数据泄漏
env     ：能看所有配置值（含密码）
loggers ：能改日志级别（把 DEBUG 打开 → 敏感信息进日志）
→ 生产只暴露 health/info/metrics/prometheus
```

### Q4：liveness 和 readiness 该放什么检查？

```text
liveness（失败 → 重启容器）：只放"进程级"——进程卡死/死锁
  ★ 不能放数据库/下游依赖（否则下游挂了 → 容器被反复重启 → 越重启越糟）
readiness（失败 → 摘流量）：放"依赖就绪"——DB/Redis/Kafka 连通性
```

### Q5：指标标签（tag）为什么要控制基数？

```text
每个 tag 组合 = 一条独立时间序列
  → 用 taskId/用户ID 当 tag → 几十万序列 → Prometheus 内存爆炸（卡片性）
正确做法：低基数维度（module/method/status），业务实体放日志/追踪，不放指标
```

### Q6：micrometer 与 prometheus 的关系？

```text
Micrometer = 门面（打点 API）；Prometheus = 其中一个后端实现（registry）
→ 业务代码只依赖 MeterRegistry（换后端不改代码）
→ 加 micrometer-registry-prometheus 才有 /actuator/prometheus 端点
```

<a id="sb5-9"></a>
## 九、一句话

```text
Actuator = 现成的仪表盘（health/metrics/env...），先把 exposure 配对；
Micrometer = 自己打点的门面（Counter/Gauge/Timer + 低基数标签）；
Prometheus 抓 /actuator/prometheus，Grafana 出图。
可观测三件套：指标（Micrometer）、日志（结构化+MDC）、追踪（Tracing）——本课是第一条腿。
```
