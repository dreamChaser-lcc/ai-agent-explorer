# SB4 · 内嵌容器与部署（Tomcat 调优 / 优雅停机 / Dockerfile 拆解）

> **一课目标**：搞懂 Spring Boot 的"内嵌容器"怎么跑、怎么调、怎么优雅地停；
> 以及生产部署的 Dockerfile 每一行为什么这么写（本项目的 `backend/Dockerfile` 就是标准答案）。
> **前置**：SP7（Actuator 与打包部署）、SP5（启动流程）
> **本项目现状**：`server.shutdown: graceful` 已开（application.yml L91）；Dockerfile 多阶段完备

---

## 目录

- [一、内嵌容器是什么（三选一 + 为什么内嵌）](#sb4-1)
- [二、Tomcat 调优（线程/连接/压缩/超时）](#sb4-2)
- [三、优雅停机（项目已开 graceful）](#sb4-3)
- [四、启动加速（懒加载/AOT/CDS）](#sb4-4)
- [五、项目 Dockerfile 逐段拆解（多阶段/缓存/非 root/--chown）](#sb4-5)
- [六、容器内存与 JVM（为什么是 MaxRAMPercentage=75）](#sb4-6)
- [七、健康探针与部署 Checklist](#sb4-7)
- [八、自测题（答案）](#sb4-8)
- [九、一句话](#sb4-9)

---

<a id="sb4-1"></a>
## 一、内嵌容器是什么（三选一 + 为什么内嵌）

```text
传统部署：应用打成 war → 丢进外部 Tomcat 的 webapps → 容器不是你的
内嵌部署：应用打成一个可执行 jar（fat jar）→ 里面自带 Servlet 容器 → java -jar 就能跑

Spring Boot 支持三个内嵌容器：
  Tomcat（默认，spring-boot-starter-web 自带）
  Jetty（替换：exclude tomcat + 加 jetty starter）
  Undertow（性能高、内存小、非阻塞；exclude tomcat + 加 undertow starter）

★ 为什么内嵌成了主流：
  ① 部署简单（一个 jar = 一切，无外部容器配置）
  ② 版本自控（容器版本随应用走，不受运维环境限制）
  ③ 云原生友好（容器镜像里只跑 java -jar，无"装 Tomcat"步骤）
  ④ 测试友好（@SpringBootTest 启动的是真容器）
```

**怎么判断当前用的哪个容器**：

```text
--debug 的 report 里：
  ServletWebServerFactoryAutoConfiguration matched
    EmbeddedTomcat / EmbeddedJetty / EmbeddedUndertow 只有一个 Positive
或启动日志：
  Tomcat initialized with port 8080 (http)
  Tomcat started on port 8080 (http)
```

<a id="sb4-2"></a>
## 二、Tomcat 调优（线程/连接/压缩/超时）

```yaml
server:
  port: ${SERVER_PORT:8080}

  # ---------- 线程池（谁处理请求） ----------
  tomcat:
    threads:
      max: 200                  # 最大工作线程（CPU 密集就小、IO 密集就大）
      min-spare: 10             # 最小空闲线程（预热用）
    accept-count: 100           # 等待队列（线程全忙时排队的请求数）★ 溢出直接拒绝
    max-connections: 8192       # 最大连接数（超过后 accept-count 也满 → 拒绝）

  # ---------- 连接与超时 ----------
  connection-timeout: 20s       # 建立 TCP 连接的超时（不是请求处理超时！）
  keep-alive-timeout: 60s       # keep-alive 空闲超时（对应 Tomcat keepAliveTimeout）

  # ---------- 压缩（省带宽，费 CPU） ----------
  compression:
    enabled: true
    mime-types: application/json,text/html,text/plain
    min-response-size: 1024     # 小于 1KB 不压（压了反而更慢）
```

```text
★ 四个参数的关系（排障必懂）：
  请求进来 → 线程池有空闲线程 → 立即处理
           → 没空闲 → 进 accept-count 队列（排队）
           → 队列满 → 直接拒绝（Connection refused / 503）

  max-connections：Tomcat 能同时建立的连接数（含空闲 keep-alive）
  accept-count：连接已建立但还没线程处理的请求排队数
  threads.max：真正干活的工作线程数
  调优顺序：先看 accept-count 溢出（线程不够）→ 再看线程是否被阻塞（慢依赖）→ 最后才调大数值
```

**本项目（agent 任务型应用）的取舍**：

```text
特点：任务提交是快的（发消息/入库），慢的是后台异步执行（不进 Web 线程）
→ Web 线程池不需要很大（默认 200 够用），主要瓶颈在异步任务/LLM 调用
→ 真正要调的是"异步线程池"（researchTaskExecutor）与 LLM 超时，不是 Tomcat 线程
```

<a id="sb4-3"></a>
## 三、优雅停机（项目已开 graceful）

**项目现状**（application.yml L91）：

```yaml
server:
  shutdown: graceful
```

**它做了什么**：

```text
收到 SIGTERM（docker stop / k8s delete pod）后：
  ① 停止接收新请求（连接层关闭）
  ② 等待正在处理的请求完成（在超时窗口内）
  ③ 关闭容器/连接池/线程池
  ④ 进程退出

没有 graceful 时：SIGTERM → 立即杀 → 进行中的请求直接断（客户端 502/连接重置）
```

**配套参数（经常漏）**：

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 每个关闭阶段最多等多久（默认 30s）
```

```text
★ 两个超时要对齐：
  server.shutdown=graceful 的等待 ≤ spring.lifecycle.timeout-per-shutdown-phase
  而 K8s 的 terminationGracePeriodSeconds 又要 ≥ 上面这个（否则 k8s 强杀）
  → 生产建议链路：k8s grace（60s） ≥ lifecycle（30s） ≥ 最长请求处理时间
```

**验证方法（Linux/macOS）**：

```bash
# 启动应用后，发一个慢请求，同时发 SIGTERM
curl "http://localhost:8080/api/tasks" -X POST ... &     # 长任务
kill -TERM <pid>                                          # 触发优雅停机
# 观察日志：Graceful shutdown ... 然后等待请求完成再退出
```

**K8s 里的注意点**：

```yaml
# 两个必须配
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 5"]   # 给负载均衡摘流的时间（否则 SIGTERM 时流量还在打）
terminationGracePeriodSeconds: 60        # 要给够优雅停机的窗口
```

<a id="sb4-4"></a>
## 四、启动加速（懒加载/AOT/CDS）

```text
① 懒加载（最快的开关，但有坑）
   spring.main.lazy-initialization: true
   → 启动时只创建必要 Bean，其余首次访问才创建（启动快、首请求变慢）
   ★ 坑：启动期校验（如 Bean 装配错误、数据库连通）被推迟到运行时才暴露

② AOT（Boot 3 的重点）
   mvn spring-boot:build-image 或 mvn -Pnative native:compile
   → 构建期生成代码/优化启动，Native Image 启动可到 100ms 级（但构建慢、反射限制）

③ CDS（Class Data Sharing，JVM 层）
   -XX:ArchiveClassesAtExit=app.jsa（首次启动生成归档）→ 后续 -XX:SharedArchiveFile 加速
   → 通常配合 AOT 用，Boot 3 有 spring-boot:process-aot

④ 减少启动工作（最实在）
   去掉不必要的自动配置（exclude）
   关闭启动时的远程校验（如 Nacos/Redis 的提前连接）
   延迟加载重型资源（大数据集/外部连接）
```

**本项目可做的两件事**：

```text
① lite 模式已经把"启动时连中间件"全关了（Redisson 排除 / Kafka auto-startup=false / Nacos 关）
   → 这就是最有效的启动加速（8~20 秒即可跑）
② 真要上线提速：AOT + CDS（SB7 会做虚拟线程实验，AOT 留给后续）
```

<a id="sb4-5"></a>
## 五、项目 Dockerfile 逐段拆解（`backend/Dockerfile`）

**完整文件**（含注释）：

```dockerfile
# ============================================================
# research-agent-backend 多阶段构建 Dockerfile
# 第1阶段：Maven 完整镜像负责编译（编译工具不进入最终镜像）
# 第2阶段：精简 JRE 镜像只负责运行（镜像体积小、启动快）
# ============================================================

# ---------- 第1阶段：编译 ----------
FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先复制 pom.xml 和 Maven 镜像源配置，单独下载依赖（利用 Docker 分层缓存）
COPY pom.xml ./
COPY docker/maven-settings.xml /root/.m2/settings.xml
RUN mvn dependency:go-offline -B

# 再复制源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- 第2阶段：运行 ----------
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine

ENV TZ=Asia/Shanghai

# 创建非 root 用户运行应用（企业安全规范）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
# ★ 用 --chown 一步完成"复制 + 改属主"
COPY --chown=appuser:appgroup --from=builder /build/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

**逐段讲清"为什么这么写"**：

| 写法 | 为什么 |
|---|---|
| **多阶段**（builder + runtime） | 编译工具（Maven/JDK 全量 ~700MB）不进最终镜像；最终镜像只剩 JRE + jar |
| `COPY pom.xml` + `mvn dependency:go-offline` **先于** `COPY src` | **分层缓存**：改代码时 pom 没变 → 依赖层命中缓存，不重新下载（构建从 5 分钟降到 30 秒） |
| `docker.m.daocloud.io` 加速源 | 国内直连 docker.io 不通（拉基础镜像超时） |
| `mvn clean package -DskipTests` | 镜像构建不跑单测（单测在 CI 跑，镜像只管打包） |
| **`adduser` + `USER appuser`** | 非 root 运行（企业安全规范；root 逃逸风险） |
| **`COPY --chown=...` 一步到位** | 分两步写（COPY + RUN chown）会让 100MB 的 jar 在新层再复制一份，**镜像白胖 100MB** |
| `TZ=Asia/Shanghai` | 日志时间与本地一致（排查方便） |
| **`MaxRAMPercentage=75.0`** | 容器内存限制下，JVM 按百分比取堆（见第六节） |
| `UseG1GC` | 大堆默认推荐（延迟/吞吐均衡） |
| `ENTRYPOINT ["sh","-c","java $JAVA_OPTS ..."]` | 支持**运行时覆盖** JAVA_OPTS（compose/k8s 注入） |
| `SPRING_PROFILES_ACTIVE=dev` 默认 | 容器内可独立运行（H2 内存库）；compose 会覆盖成 dev-docker |

**可以改进的两点（讲义补充）**：

```text
① 加 HEALTHCHECK（或依赖 k8s 探针）：
   HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
     CMD wget -qO- http://localhost:8080/actuator/health || exit 1

② 加 JVM 容器感知参数（JDK 21 默认已开启容器感知，可选）：
   -XX:+UseContainerSupport（默认开）
   -XX:MaxRAMPercentage=75.0（已配）
```

<a id="sb4-6"></a>
## 六、容器内存与 JVM（为什么是 MaxRAMPercentage=75）

```text
容器内存限制 = 1GB 时：
  ❌ -Xmx1g → 堆 1GB + 元空间 + 线程栈 + 直接内存 → 总超 1GB → OOMKilled
  ✅ -XX:MaxRAMPercentage=75.0 → 堆 = 容器内存 × 75% = 750MB，其余留给非堆

★ 为什么是 75 不是 100：
  堆外还有：元空间（Metaspace）、线程栈（每线程 1MB × 200 线程 = 200MB）、
           直接内存（Netty/MappedByteBuffer）、JVM 自身开销
  → 75% 是经验值（大内存场景 70~80%）

★ 怎么验证生效：
  java -XX:+PrintFlagsFinal -version | grep MaxHeapSize
  或容器内 /actuator/metrics/jvm.memory.max
```

**本项目（H2 文件库 + 大模型调用）的内存结构**：

```text
堆：业务对象 + 缓存（内存缓存版）+ 任务上下文（长任务跑在消费者线程）
非堆：元空间（Spring 大量类）+ 直接内存（Kafka/HTTP 客户端）
外部：H2 文件库（page cache 走 OS，不占 JVM 堆）
→ 75% 合理；如果上 Native/虚拟线程，需重新评估线程栈占用
```

<a id="sb4-7"></a>
## 七、健康探针与部署 Checklist

**Actuator 三个探针（配合 K8s）**：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true          # 暴露 /actuator/health/liveness 和 /readiness
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

```yaml
# K8s 探针示例
livenessProbe:              # 活着吗（失败 → 重启容器）
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:             # 能接流量吗（失败 → 从 Service 摘除）
  httpGet: { path: /actuator/health/readiness, port: 8080 }
  periodSeconds: 5
startupProbe:               # 启动期探针（避免慢启动被杀）
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  failureThreshold: 30
  periodSeconds: 5
```

**部署 Checklist（上线前逐项打勾）**：

```text
□ 镜像：多阶段 + 非 root + 分层缓存 + 加速源
□ JVM：MaxRAMPercentage（不要写死 -Xmx）+ G1GC + （可选）AOT/CDS
□ 优雅停机：server.shutdown=graceful + lifecycle.timeout + k8s preStop/grace
□ 健康探针：liveness / readiness / startup 三个都配（慢启动用 startupProbe）
□ 超时：Tomcat connection-timeout + 业务 HTTP 客户端超时（别用默认无限）
□ 日志：结构化（JSON）+ 容器 stdout（不要写文件）+ MDC traceId
□ 线程池：Tomcat threads / 异步线程池 / 连接池（大小对齐）
□ 安全：非 root + 只读文件系统（可选）+ 密钥走 Secret
```

<a id="sb4-8"></a>
## 八、自测题（答案）

### Q1：内嵌容器（Tomcat）与应用的生命周期关系？

```text
同一个进程：java -jar 启动 → 创建 Spring 容器 → 内嵌 Tomcat 随之启动
停机：SIGTERM → graceful 等待请求完成 → 关闭 Tomcat → 关闭 Spring 容器 → 进程退出
→ 容器和应用"同生共死"（不像外部 Tomcat 那样可独立重启容器）
```

### Q2：threads.max、accept-count、max-connections 三者的关系？

```text
连接层（max-connections）→ 排队层（accept-count）→ 处理层（threads.max）
请求链路：建立连接 → 若线程有空闲立即处理 → 否则进 accept-count 队列 → 队列满则拒绝
排障：accept-count 溢出 = 线程不够或线程被阻塞；先查慢依赖再加线程
```

### Q3：为什么 Dockerfile 要先 COPY pom.xml 再 COPY src？

```text
Docker 分层缓存：每条指令一层，某层输入不变就复用缓存
COPY pom.xml + mvn go-offline 放前面 → 代码改动但 pom 没变时，依赖层命中缓存
→ 构建时间从"重新下载全部依赖"降到"只编译源码"
```

### Q4：为什么 COPY 要带 --chown？不带会怎样？

```text
分两步（COPY + RUN chown）会让 jar 在新层里再复制一份（镜像多 ~100MB）
--chown 一步完成复制+改属主 → 镜像不变胖
```

### Q5：MaxRAMPercentage=75 为什么不是 100？

```text
堆外还有开销：元空间 + 线程栈 + 直接内存 + JVM 自身
堆占 100% → 容器总内存超标 → OOMKilled
75% 是经验值（给非堆留 25%）
```

### Q6：优雅停机在 K8s 里还差什么？

```text
还差两件：
① preStop.sleep(5~10s)：等负载均衡摘流（否则 SIGTERM 时新流量还在打进来）
② terminationGracePeriodSeconds ≥ spring.lifecycle.timeout-per-shutdown-phase
否则 k8s 会在应用还没停完时强杀
```

<a id="sb4-9"></a>
## 九、一句话

```text
内嵌容器 = 应用自带服务器（jar 即一切）。
调优看三件事：线程（threads）、队列（accept-count）、连接（max-connections）——
先找阻塞点，再调数值。
优雅停机 = SIGTERM 后"停止收新请求 + 等处理完 + 再退出"，
链路要对齐：k8s grace ≥ lifecycle timeout ≥ 最长请求。
Dockerfile 的精髓只有两条：多阶段（工具不进镜像）+ 分层缓存（先 pom 后 src）+ 非 root。
```
