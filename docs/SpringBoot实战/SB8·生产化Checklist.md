# SB8 · 生产化 Checklist（把前 7 课串成上线前逐项打勾）

> **一课目标**：不再学新知识，而是把 SB1–SB7 + SP 线的产出**串成一张可执行的上线清单**，
> 并给本项目逐项打分（已做 / 待办 / 证据）。
> **定位**：本课是"复查课"——上线前照着表走一遍。
> **本课产出**：8 大块 Checklist + 本项目评分表

---

## 目录

- [一、8 大块总览（上线前先看这张）](#sb8-1)
- [二、启动与 JVM（内存/参数/启动时间）](#sb8-2)
- [三、线程池四类（Tomcat/异步/Kafka/调度）](#sb8-3)
- [四、连接池（Hikari/Redis/HTTP）](#sb8-4)
- [五、超时预算（分层传递）](#sb8-5)
- [六、日志（结构化/MDC/级别/脱敏）](#sb8-6)
- [七、健康检查与优雅下线](#sb8-7)
- [八、安全（密钥/Actuator/依赖）](#sb8-8)
- [九、★ 本项目 Checklist 评分表](#sb8-9)
- [十、自测题（答案）](#sb8-10)
- [十一、一句话](#sb8-11)

---

<a id="sb8-1"></a>
## 一、8 大块总览（上线前先看这张）

| # | 块 | 核心问题 | 对应课程 |
|---|---|---|---|
| 1 | 启动与 JVM | 内存够吗？参数对吗？ | SB4 |
| 2 | 线程池 | 四类池子的拒绝策略与大小 | SB4/SP9 |
| 3 | 连接池 | 数据库/缓存/HTTP 连接够吗？ | SP9/场景⑦ |
| 4 | 超时 | 每一层都有超时吗？（最容易漏） | 本课 |
| 5 | 日志 | 能定位问题吗？会泄漏吗？ | 本课 |
| 6 | 健康与下线 | 能探测吗？能优雅停吗？ | SB4/SB5 |
| 7 | 安全 | 密钥/端点/依赖 | SB3/SB5 |
| 8 | 可观测 | 指标/日志/追踪 | SB5 |

<a id="sb8-2"></a>
## 二、启动与 JVM（内存/参数/启动时间）

```text
□ JVM 内存参数
  ✅ 容器环境用 -XX:MaxRAMPercentage=75（本项目 Dockerfile 已配）
  ❌ 不要写死 -Xmx（容器内存变了会 OOMKilled）
  □ 元空间：-XX:MaxMetaspaceSize（大应用/大量动态类时才需要）

□ GC 选择
  ✅ 大堆（>4G）用 G1（本项目已配 -XX:+UseG1GC）
  · 低延迟敏感：ZGC/Shenandoah（JDK 21 可用）

□ 启动时间
  □ 记录启动耗时（Started ... in X seconds）
  □ 慢启动（>30s）→ 查：自动配置太多/外部连接阻塞/AOT
  □ K8s 用 startupProbe 兜住慢启动

□ 启动期副作用
  □ 不要在启动时做重活（大数据预热/全表扫描）
  □ 外部依赖连接失败要降级（optional 配置/懒连接）——本项目 lite 模式就是范例
```

<a id="sb8-3"></a>
## 三、线程池四类（Tomcat/异步/Kafka/调度）——**最容易互相打架**

| 池子 | 谁创建 | 本项目配置 | 关键参数 |
|---|---|---|---|
| **Tomcat 工作线程** | 内嵌容器 | 默认（200） | threads.max / accept-count / max-connections（SB4） |
| **异步任务线程池** | 项目自定义 | `researchTaskExecutor`（场景⑥） | core/max/queue/**拒绝策略** |
| **Kafka 消费线程** | spring-kafka 容器 | concurrency 默认 1 | concurrency ≤ 分区数 |
| **定时任务调度** | Spring Task | @Scheduled 默认单线程 | pool.size（多任务要调大） |

```text
★ 三个必查：
① 拒绝策略：异步池满了怎么办？（AbortPolicy 抛异常 / CallerRunsPolicy 反压 / Discard）
   → 本项目 researchTaskExecutor 的拒绝策略要看代码（满了是丢任务还是报错？）
② 隔离：不同业务共用线程池 = 互相拖死（任务 A 卡住 → 任务 B 排队）
   → 核心业务独立池（本项目只有执行池，暂可接受；扩展时拆）
③ ThreadLocal 泄漏：线程池复用线程 → 上一个请求的 ThreadLocal 残留
   → 用完 remove()（MDC/traceId 尤其注意！）
```

**本项目对照**：

```text
✅ 有独立 researchTaskExecutor（不与 Web 线程混）
⚠️ 参数（core/max/queue/拒绝策略）需要复核（场景⑥ 只关注了异步化）
⚠️ @Scheduled 有多个任务（CleanupScheduler 等）→ 确认 pool.size 或串行可接受
```

<a id="sb8-4"></a>
## 四、连接池（Hikari/Redis/HTTP）

```yaml
# 数据库（Hikari）
spring:
  datasource:
    hikari:
      maximum-pool-size: 10        # 默认 10 —— 高并发下是瓶颈（SB7 虚拟线程那节提过）
      minimum-idle: 5
      connection-timeout: 3000     # 拿连接超时（默认 30s！太长）
      idle-timeout: 600000
      max-lifetime: 1800000        # 必须 < DB 的 wait_timeout

# Redis（Lettuce 连接池可选）
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: 3000ms

# HTTP 客户端（RestTemplate/WebClient）
#   → 连接池（PoolingHttpClientConnectionManager）+ 超时（见第五节）
```

```text
★ Hikari 两个最常踩的坑：
① connection-timeout 默认 30s 太长 → 设为 3s（快速失败）
② max-lifetime 必须小于 DB/网络设备的最大空闲时间（否则拿到失效连接）
```

### 附：HikariCP 详解（2026-11-26 学员问）

**快速结论（三句话）**：

```text
① 不用加依赖：HikariCP 是 Spring Boot 默认连接池；data-jpa / mybatis-starter 都自带
   （本项目 pom L76 spring-boot-starter-data-jpa、L81 mybatis-spring-boot-starter 已满足）
② 它是什么：管理"到数据库的连接复用"——池里保持 N 条连接，请求借用/归还，避免每次建连
③ 怎么确认在工作：启动日志 "HikariPool-1 - Starting... / Start completed"，
   或 /actuator/metrics/hikaricp.connections.active（SB5 的 metrics 已开）
```

**项目现状（2026-11-26 核查）**：

```text
application.yml 只有：
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/research_agent}
→ 没有任何 hikari 参数 = 全用默认值（池 10 / 拿连接等 30s / 连接活 30min）
→ lite/dev（H2 文件库，嵌入式）不用配；dev-docker/cluster（PG）建议配
```


**要不要加依赖？不要。**

```text
HikariCP = Spring Boot 默认 JDBC 连接池（性能最好的实现之一）
传递依赖来源（本项目已有）：
  spring-boot-starter-data-jpa      （pom L76）
  mybatis-spring-boot-starter       （pom L81）
→ 在 yml 写 spring.datasource.hikari.* 就生效，无需改 pom

验证方式：
  ① 启动日志：HikariPool-1 - Starting... / HikariPool-1 - Start completed
  ② 指标：/actuator/metrics/hikaricp.connections.active（SB5 的 metrics 已开）
```

**五个参数（本项目现状：未配置 = 全默认）**：

| 参数 | 默认 | 作用 | 建议 |
|---|---|---|---|
| `maximum-pool-size` | 10 | 池最大连接数 | 10~20（PG max_connections=100，多实例分摊） |
| `minimum-idle` | **= maximum** | 最少空闲连接 | 设 5（避免常驻 10 个） |
| `connection-timeout` | **30000ms** | 从池拿连接的等待上限 | **3000ms**（快速失败；30s 拖垮线程池） |
| `idle-timeout` | 600000ms | 空闲连接回收 | 默认即可 |
| `max-lifetime` | 1800000ms | 连接最长寿命 | **必须 < DB wait_timeout**（PG 默认不超时 → 30min 安全） |

**本项目该不该配**：

```text
lite/dev（H2 文件库，嵌入式）→ 连接池意义小 → 可不配
dev-docker/cluster（PG，默认 datasource）→ 建议配 Hikari 参数
```

**三个坑**：

```text
① minimum-idle 默认等于 maximum → 启动即建满 10 个连接（DB 侧浪费）
② max-lifetime > DB 空闲超时 → 拿到失效连接（Communications link failure）
③ connection-timeout 默认 30s → 高并发下线程被占死（同"超时预算"一节的逻辑）
```

**PG 环境推荐配置（可直接抄）**：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
```
<a id="sb8-5"></a>
## 五、超时预算（分层传递）——**最容易漏的一块**

```text
请求进来 → 网关 → Web 层 → Service → 外部调用（LLM/DB/HTTP）
每一层都要有超时，且【外层 > 内层】（否则内层还没超时就白干了）

预算示例：
  网关 30s  >  Web 25s  >  Service 20s  >  LLM 调用 15s  >  连接 3s + 读 15s

★ 关键认知：没有超时的调用 = 可能永远挂着（线程被占死 → 雪崩）
```

**本项目检查点**：

```text
✅ LLM 客户端：读超时 300s（application.yml app.llm.*，长任务需要）
⚠️ DB：Hikari connection-timeout 默认 30s（建议 3s）
⚠️ HTTP 客户端（RestTemplate/HttpClient）：是否有 connect/read timeout（需核查）
⚠️ Kafka：producer 的 delivery.timeout.ms / consumer 的 poll-timeout（默认值是否够）
□ Tomcat connection-timeout（默认 20s，SB4 讲过）
```

```text
★ 排障口诀：接口"卡住不返回"→ 先查调用链上哪一层没超时
```

<a id="sb8-6"></a>
## 六、日志（结构化/MDC/级别/脱敏）

```text
□ 输出目标：容器 stdout（不要写文件——容器文件随容器消失）
□ 结构化：JSON 格式（便于 ELK/Loki 解析）
   Boot 3.4+ 原生支持：logging.structured.format.console=ecs（或 logstash）
□ 链路：MDC 放 traceId（本项目 RequestTraceFilter 已在做？——确认）
   格式：%d{...} [%X{traceId}] %-5level ...
□ 级别：root=INFO（生产）；关键包可 DEBUG 临时开（actuator/loggers 动态改）
□ 脱敏：密钥/手机号/身份证 → 日志里不能出现（LLM 的 api-key 尤其注意！）
□ 滚动：容器场景交给 stdout + 日志平台；非容器用 logback 滚动策略
```

**本项目对照**：

```text
✅ 有请求追踪（RequestTraceFilter + 日志链路）
⚠️ 日志非结构化（logback 默认 pattern，非 JSON）→ 生产建议 logging.structured
⚠️ 密钥脱敏：确认日志里不会打印 OPENAI_API_KEY（grep 一下）
□ MDC 清理：filter 结束时 MDC.clear()（线程池复用会残留）
```

<a id="sb8-7"></a>
## 七、健康检查与优雅下线

```yaml
# 健康（SB5）
management:
  endpoint:
    health:
      probes:
        enabled: true          # liveness/readiness 分开
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

# 优雅停机（SB4）
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```text
□ K8s 三探针：liveness / readiness / startup（慢启动）
□ preStop.sleep(5~10s)：等负载均衡摘流
□ terminationGracePeriodSeconds ≥ lifecycle timeout
□ 关闭钩子：@PreDestroy / DisposableBean（线程池/连接池优雅关闭）
```

**本项目对照**：

```text
✅ shutdown=graceful（yml L91）
⚠️ lifecycle.timeout-per-shutdown-phase 未显式配（默认 30s，可接受但要明确）
⚠️ probes.enabled 未开（liveness/readiness 端点不可用）
⚠️ K8s 部署时的 preStop/grace 未配置（部署侧待办）
```

<a id="sb8-8"></a>
## 八、安全（密钥/Actuator/依赖）

```text
□ 密钥：
  ✅ 环境变量注入（本项目 start.ps1 / docker/.env）
  ⚠️ 明文兜底在 yml（application-llm.yml 的 ${OPENAI_API_KEY:sk-...}）→ 生产必须删兜底
  □ 生产换 K8s Secret / Vault
□ Actuator：
  ⚠️ 当前暴露 health,info,env,metrics —— env 生产必须关！（能看密钥）
  □ 生产只开 health,info,metrics,prometheus（+ 独立端口 8081 内网）
  □ 加 Spring Security 保护 /actuator/**
□ 依赖：
  □ mvn dependency-check / OWASP（漏洞扫描）
  □ 锁版本（父 pom 统一管理）
□ 其他：
  □ 非 root 运行（Dockerfile 已做）
  □ 只读文件系统（可选）
  □ CSRF/鉴权（项目目前无认证？—— 上线前确认访问控制）
```

<a id="sb8-9"></a>
## 九、★ 本项目 Checklist 评分表（2026-11-26 复查）

| # | 项 | 状态 | 证据 / 待办 |
|---|---|---|---|
| 1 | JVM 内存参数 | ✅ | Dockerfile：`MaxRAMPercentage=75.0 -XX:+UseG1GC` |
| 2 | 优雅停机 | ✅ | yml `server.shutdown: graceful`（L91） |
| 3 | 停机超时 | ⚠️ | 未显式配 `lifecycle.timeout-per-shutdown-phase`（默认 30s） |
| 4 | 健康探针 | ⚠️ | 未开 `probes.enabled`（liveness/readiness 不可用） |
| 5 | Actuator 暴露 | ⚠️ | 含 `env`（生产必须移出）；建议独立端口 |
| 6 | Prometheus 指标 | ❌ | 未引 `micrometer-registry-prometheus` |
| 7 | 线程池 | ✅/⚠️ | 有独立 `researchTaskExecutor`；拒绝策略待复核 |
| 8 | 连接池 | ⚠️ | Hikari 默认 10 / connection-timeout 30s（建议 3s） |
| 9 | 超时 | ✅/⚠️ | LLM 读超时 300s 已配；HTTP 客户端超时待核查 |
| 10 | 日志链路 | ✅ | RequestTraceFilter + 请求日志（有 traceId） |
| 11 | 结构化日志 | ❌ | 未开 `logging.structured`（非 JSON） |
| 12 | 日志脱敏 | ⚠️ | 需确认 api-key 不会进日志 |
| 13 | Kafka 消费 | ✅ | `ack-mode=record` + `max-poll-records=1` + DLT（M4/M5） |
| 14 | 缓存 | ✅ | `@Cacheable` + JSON 序列化 + TTL 30min（场景⑦） |
| 15 | 密钥管理 | ⚠️ | 环境变量 + **明文兜底**（生产需删兜底 + Secret） |
| 16 | 依赖扫描 | ❌ | 未接 OWASP/dependency-check |
| 17 | 认证鉴权 | ❌ | 项目当前无认证（上线前必须加或前置网关） |
| 18 | 数据库迁移 | ✅ | Flyway（cluster profile）+ schema-dev.sql（dev） |

**结论**：

```text
已完成（含证据）：9 项
需要补（上线前）：6 项（停顿超时/探针/Prometheus/结构化日志/连接池参数/依赖扫描）
必须解决（安全）：2 项（env 暴露移出 / 明文兜底删除 + 认证鉴权）
```

**优先修复顺序（建议）**：

```text
① env 端点移出（1 分钟，安全第一）
② 明文兜底删除 + 密钥走环境变量/Secret（10 分钟）
③ 认证鉴权（前置网关或 Spring Security，按需）
④ 连接池参数（Hikari max-pool-size + connection-timeout=3s，10 分钟）
⑤ 健康探针 probes.enabled + lifecycle timeout（30 分钟）
⑥ Prometheus 依赖 + 独立端口（1 小时）
⑦ 结构化日志 + 依赖扫描（按团队规范）
```

<a id="sb8-10"></a>
## 十、自测题（答案）

### Q1：为什么 `connection-timeout` 默认 30s 被认为"太长"？

```text
连接池拿不到连接时，请求会等 30s 才失败 → 线程被占 30s → 并发高时线程池被打空 → 雪崩
→ 生产建议 3s（快速失败 + 让上层重试/降级）
```

### Q2：为什么超时要"外层 > 内层"？

```text
外层（如 Web 25s）必须大于内层（如 LLM 15s）：
否则外层先超时返回了，内层还在白干活（资源浪费）
→ 超时预算从外到内逐层递减，留出处理余量
```

### Q3：容器里为什么把日志写文件是反模式？

```text
容器文件系统随容器销毁而消失（除非挂卷）→ 日志丢失
且无法被 k8s/日志平台统一采集
→ 正确：写 stdout，由容器运行时/日志侧车收集（Loki/ELK）
```

### Q4：@Scheduled 默认单线程会带来什么问题？

```text
所有 @Scheduled 任务串行执行 → 一个慢任务阻塞其他所有定时任务
→ 多任务场景要配 pool.size（spring.task.scheduling.pool.size）或 @Async 拆分
```

### Q5：为什么说 env 端点必须移出生产暴露列表？

```text
/actuator/env 会返回所有配置源的值（含数据库密码、API Key 等）
→ 等于把密钥公开（且是 JSON，机器可读）
→ 生产只暴露 health/info/metrics/prometheus，且走独立端口内网
```

<a id="sb8-11"></a>
## 十一、一句话

```text
上线前 8 查：JVM / 线程池 / 连接池 / 超时 / 日志 / 健康下线 / 安全 / 可观测。
本项目打分：9 项已完成，6 项待补，2 项安全必改（env 暴露 + 明文兜底）。
这张表就是这 8 课的落点——不是学完，是能照着表把系统调到可上线。
```
