# SP7 · Actuator 与打包部署

> **课程**：Spring 基础实战 · 模块二（2026-10-21）
> **已会**：部署面的 K8s 探针（liveness/readiness）、`JAVA_OPTS`；SP5 自动配置、SP6 配置优先级
> **本次**：**Actuator 端点全景**（health/metrics/env/beans/conditions）、暴露策略与安全边界、**fat jar 结构拆解**、`java -jar` 独立启动
> **对标**：廖雪峰 23.2 / 23.3 / 23.4
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **部署面**：K8s 用 `livenessProbe` / `readinessProbe` 探活——**探的就是 Actuator 的 health 端点**（Spring Boot 2.3+ 的 `health/liveness`、`health/readiness`）；
- **SP5 自动配置**：`/actuator/conditions` 能看到"每个自动配置类的条件评估结果"（本课实测）；
- **SP6 配置优先级**：`/actuator/env` 能看到"某个配置项从哪个来源来"（本课实测）；
- **项目现状**：已引 `spring-boot-starter-actuator`，暴露 `health,info,env`（`application.yml` 的 `management` 段）。

---

## 一、Actuator：生产就绪的"仪表盘"

### 1.1 是什么

一组**内置 HTTP 端点**，暴露应用运行时状态——不写一行代码，就能看到：健康、指标、配置、Bean、自动配置条件、日志级别、线程栈……

### 1.2 端点全景（最常用）

| 端点 | 作用 | 敏感度 |
|---|---|---|
| `/actuator/health` | 健康状态（DB/Redis/Kafka 等组件汇总） | 低（K8s 探针用） |
| `/actuator/info` | 应用信息（版本/构建时间） | 低 |
| `/actuator/metrics` | 指标（JVM/HTTP/连接池/自定义 Counter） | 中 |
| `/actuator/env` | **配置项来源与取值** | **高**（可能泄漏密钥） |
| `/actuator/beans` | 容器内所有 bean | 中 |
| `/actuator/conditions` | **自动配置条件评估报告** | 中（诊断神器） |
| `/actuator/loggers` | 运行时改日志级别（可 POST 修改） | 中 |
| `/actuator/threaddump` | 线程栈快照 | 中 |
| `/actuator/heapdump` | 堆转储（文件下载） | **极高** |
| `/actuator/shutdown` | 远程关闭应用（默认禁用） | **极高** |

### 1.3 暴露策略（安全边界）

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info          # 生产建议：只开这两个
        # include: "*"                # ★ 危险：全部暴露
  endpoint:
    health:
      show-details: when-authorized   # 健康详情只给授权用户看
```

**铁律**：`env` / `heapdump` / `shutdown` **绝不开放到公网**——本项目 dev 开 `env` 只是学习观察窗（yml 里有注释说明）。

---

## 二、health 端点与 K8s 探针（串部署面）

### 2.1 两个专用探针端点

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true     # 开启 /actuator/health/liveness 与 /readiness
```

| 端点 | K8s 探针 | 含义 |
|---|---|---|
| `/actuator/health/liveness` | livenessProbe | 进程"活着"（不该被重启时返回 UP） |
| `/actuator/health/readiness` | readinessProbe | "能接流量"（启动中/依赖未就绪返回 DOWN） |

**区别**：liveness 失败 → K8s **重启** Pod；readiness 失败 → K8s **摘掉流量**但不重启。**配错会导致"起不来就无限重启"**。

### 2.2 自定义健康检查

实现 `HealthIndicator`：

```java
@Component
public class LlmHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 检查下游 LLM 是否可用（可做一次轻量探测）
        return Health.up().withDetail("provider", "agentrouter").build();
    }
}
```

聚合后出现在 `/actuator/health` 的 `components` 里——**下游挂了，readiness 可以主动 DOWN，把流量摘走**（和场景⑧ 的降级思路一致）。

---

## 三、fat jar：Spring Boot 的"自包含"魔术

### 3.1 结构

```text
research-agent-backend-0.0.1-SNAPSHOT.jar
├── META-INF/MANIFEST.MF       ← Main-Class: org.springframework.boot.loader.launch.JarLauncher
├── org/springframework/boot/loader/...   ← 启动器（自定义 ClassLoader）
├── BOOT-INF/classes/           ← 你的代码 + 配置文件（application.yml 在这里）
└── BOOT-INF/lib/               ← 所有依赖 jar（spring、h2、kafka……）
```

### 3.2 为什么能 `java -jar`

普通 jar 的类加载器只认 classpath；Spring Boot 的 `JarLauncher` 是**自定义启动器**：

```text
java -jar app.jar
  → MANIFEST 的 Main-Class 指向 JarLauncher
  → JarLauncher 用自定义 ClassLoader 加载 BOOT-INF/lib 里的嵌套 jar
  → 再启动 BOOT-INF/classes 里的主类（ResearchAgentApplication）
```

**所以**：fat jar 是"一个文件装下所有依赖"——服务器上只要 JDK，不需要装 Maven / 不需要 lib 目录。

### 3.3 可执行 jar vs 普通 jar

| | 可执行 fat jar | 普通 jar |
|---|---|---|
| 生成方式 | `spring-boot-maven-plugin` 的 repackage | 普通 `maven-jar-plugin` |
| 依赖 | 全打进 BOOT-INF/lib | 不带依赖 |
| 启动 | `java -jar` 即可 | 需要 `-cp` 指定所有依赖 |

---

## 四、打包与启动命令

### 4.1 打包

```powershell
cd backend
mvn clean package "-Dmaven.test.skip=true"
# 产物：target/research-agent-backend-0.0.1-SNAPSHOT.jar（116.4 MB）
```

### 4.1.1 为什么和部署面（micro-lab）的命令不一样（2026-10-21 学员提问）

| | micro-lab（部署面课） | research-agent（本课） | 原因 |
|---|---|---|---|
| 基础命令 | `mvn package` | `mvn package` | **完全一样**（都由 `spring-boot-maven-plugin` repackage 成 fat jar） |
| 跳过测试 | `-DskipTests` | `-Dmaven.test.skip=true` | 前者只跳过**运行**、测试代码仍编译；本项目**测试代码过时编译不过**（场景⑥ 改动未同步），只能用后者连编译一起跳 |
| 引号 | 不需要 | **必须** | `-Dmaven.test.skip=true` 带点号，PowerShell 会拆成 `-Dmaven` + `.test.skip=true` 报 `Unknown lifecycle phase`（实测踩过）；`-DskipTests` 无点号所以不用引号 |
| `clean` | 有 | 有（讲义早期漏写，已补） | 清理旧产物，避免把上次残留打进包 |

**三种跳过方式对比**（以后按项目状态选）：

| 参数 | 跳过运行 | 跳过编译 | 适用 |
|---|---|---|---|
| 无参数 | ❌ | ❌ | 测试健康，跑测试当回归网（**目标状态**） |
| `-DskipTests` | ✅ | ❌ | 测试代码能编译、只是不想跑 |
| `-Dmaven.test.skip=true` | ✅ | ✅ | 测试代码编译不过（**本项目现状**，场景⑫ 待修） |

> 等场景⑫ 修好测试代码，就能退回 `mvn clean package -DskipTests`，最终目标是"不跳过、让测试跑"。**跳过测试是权宜，不是常态。**

### 4.2 启动

```powershell
java -jar target/research-agent-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 4.3 生产启动参数（JAVA_OPTS）

```powershell
java -Xms512m -Xmx1024m `
     -Dfile.encoding=UTF-8 `
     -Duser.timezone=Asia/Shanghai `
     -jar app.jar --spring.profiles.active=prod --server.port=8080
```

| 参数 | 作用 |
|---|---|
| `-Xms` / `-Xmx` | 堆初始/最大值（容器里建议 `-XX:MaxRAMPercentage=75`） |
| `-Dfile.encoding=UTF-8` | 防中文乱码 |
| `-Duser.timezone` | 时区（与 DB 的 `TIMESTAMP WITH TIME ZONE` 呼应） |
| `--spring.profiles.active` | profile（SP6） |
| `--server.port` | 端口（命令行 > yml，SP6 优先级） |
---

## 五、验证记录（2026-10-21 三组实测）

### 5.1 Actuator 端点（mvn 启动的实例）

```text
/actuator 端点列表：
  self, beans, health, health-path, info, conditions, env, env-toMatch, loggers, loggers-name, metrics
/actuator/health      → {"status":"UP"}
/actuator/conditions  → positiveMatches=299，negativeMatches=342
/actuator/env         → sp6.demo.name 同时出现在 application-dev.yml 与 application.yml 两个来源
/actuator/loggers     → POST 把 com.aiexplorer 改为 DEBUG，返回 DEBUG（运行时立即生效）
/actuator/beans       → 约 200 KB JSON（全部 bean 明细）
```

**两处与前几课的呼应**：

- `conditions` 的 **299 生效 / 342 未生效** = SP5 讲的"自动配置 = 清单 + 条件"的**运行时报告**（想查某个 starter 为什么没生效，看这个端点最快）；
- `env` 显示 `sp6.demo.name` 在两个 yml 都有 = SP6 讲的**配置源叠加与优先级**的直接证据。

### 5.2 fat jar 结构（mvn package）

```text
产物：target/research-agent-backend-0.0.1-SNAPSHOT.jar（116.4 MB）
总条目 447
├── META-INF/MANIFEST.MF
├── org/springframework/boot/loader/launch/JarLauncher.class     ← 启动器
├── BOOT-INF/classes/  … 146 个 class（含 ResearchAgentApplication、application.yml）
└── BOOT-INF/lib/      … 128 个依赖 jar（spring-boot-3.5.13、h2-2.3.232、kafka-clients-3.9.2…）
```

### 5.3 `java -jar` 独立启动

```text
Exposing 3 endpoints beneath base path '/actuator'
Tomcat started on port 8080 (http)
Started ResearchAgentApplication in 15.004 seconds

/actuator/health → UP
GET /api/tasks   → 200
```

**结论**：服务器只要装了 JDK，`java -jar` 就能跑——不需要 Maven、不需要 lib 目录。

---

## 六、项目关联地图

| 概念 | 项目位置 |
|---|---|
| Actuator 依赖 | `pom.xml`（`spring-boot-starter-actuator`） |
| 暴露配置 | `application.yml` 的 `management.endpoints.web.exposure.include: health,info,env` |
| K8s 探针 | 部署面五课（liveness/readiness 探的就是 health 端点） |
| 打包插件 | `spring-boot-maven-plugin`（repackage 生成 fat jar） |
| 产物 | `target/research-agent-backend-0.0.1-SNAPSHOT.jar`（target 已在 .gitignore） |

---

## 七、思考题（自查 + 参考答案）

1. 生产环境的 `/actuator` 该暴露哪些端点？<br>
   **参考答案**：至少 `health`（K8s 探针依赖它）；`info` 可选。**`env` / `heapdump` / `loggers` / `shutdown` 绝不开放公网**——`env` 泄漏配置（含密钥）、`heapdump` 含全部内存数据、`loggers` 可被恶意改级别刷爆日志、`shutdown` 直接关服务。本项目 dev 开 `env` 是学习观察窗（yml 有注释）。
2. liveness 和 readiness 有什么区别？配反了会怎样？<br>
   **参考答案**：`liveness` 失败 → K8s **重启 Pod**；`readiness` 失败 → **摘掉流量**但不重启。配反的典型事故：把"DB 连接检查"放进 liveness → 数据库抖一下，所有 Pod 被反复重启（雪崩）。**依赖检查应放 readiness**（摘流量等恢复），liveness 只判断"进程是否还活着"。
3. fat jar 为什么能 `java -jar` 直接跑？<br>
   **参考答案**：MANIFEST 的 `Main-Class` 指向 `JarLauncher`；它用**自定义 ClassLoader**加载 `BOOT-INF/lib` 里的嵌套 jar（普通 ClassLoader 不认识嵌套 jar），再启动 `BOOT-INF/classes` 里的主类。
4. 打包时 `-Dmaven.test.skip=true` 和 `-DskipTests` 有什么区别？为什么本项目必须用前者？<br>
   **参考答案**：`-DskipTests` 只跳过**运行**测试，仍编译测试代码；`-Dmaven.test.skip=true` **连编译一起跳过**。本项目测试代码过时（场景⑥ 改了构造器/返回类型没同步），`-DskipTests` 会卡在 testCompile——**这是场景⑫ 测试体系的第一笔欠账**。

---

## 八、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| **无新增 Java 文件** | Actuator 完全靠"依赖 + 配置"生效 | — |
| `backend/target/research-agent-backend-0.0.1-SNAPSHOT.jar` | 打包产物（116.4 MB） | ✅ 可删（`mvn clean` 会清掉；target 已在 .gitignore） |

> 本课是"零代码课"——**Actuator 的价值在于"不写代码就能观测"**，正好和前面"写代码实现"的课形成对比。

---

## 九、本课问答记录（2026-10-21）

| # | 提问（原话） | 落档位置 |
|---|---|---|
| **Q1** | "这个打包命令怎么和之前的不太一样，之前的打包命令好像不是这样的" | §4.1.1「和部署面（micro-lab）打包命令的差异」——核心命令相同（`mvn package`）；差异在：① `-DskipTests` vs `-Dmaven.test.skip=true`（本项目测试代码编译不过）；② PowerShell 引号（点号会被拆）；③ `clean` 已补回 |
