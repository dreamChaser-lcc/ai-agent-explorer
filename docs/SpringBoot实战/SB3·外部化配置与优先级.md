# SB3 · 外部化配置与优先级（17 种配置源 / Profile 组合 / 加密 / 刷新）

> **一课目标**：说清"这个配置值到底从哪来的"——掌握配置源优先级矩阵、Profile 组合方式、
> 以及生产里的加密与刷新策略。
> **前置**：[SP6·配置体系与Profile](../Spring基础实战/SP6·配置体系与Profile.md)（基础用法）→ 本课讲**优先级与工程实践**
> **本课实测**：命令行参数覆盖 yml 的端口（真实启动验证）

---

## 目录

- [一、配置从哪来（17 种源 + 优先级矩阵）](#sb3-1)
- [二、优先级实测：命令行覆盖 yml（真实启动）](#sb3-2)
- [三、Profile 组合（本项目 lite + mybatis 怎么叠加）](#sb3-3)
- [四、spring.config.import（多文件 / Nacos / 配置树）](#sb3-4)
- [五、配置加密（三种方案与本项目做法）](#sb3-5)
- [六、配置刷新（@RefreshScope 与限制）](#sb3-6)
- [七、本项目实践：start.ps1 的 env 注入属于哪一层](#sb3-7)
- [八、自测题（答案）](#sb3-8)
- [九、一句话](#sb3-9)

---

<a id="sb3-1"></a>
## 一、配置从哪来（17 种源 + 优先级矩阵）

```text
Spring Boot 的配置是"多源叠加 + 优先级覆盖"：
  同一个 key 在多个源里出现 → 优先级高的赢（低优先级的被覆盖，不是合并列表）

完整优先级（从高到低，Boot 3.x）：
  ①  Devtools 全局设置（~/.spring-boot-devtools.properties，仅开发）
  ②  @TestPropertySource / @SpringBootTest(properties=...)
  ③  ★ 命令行参数（--server.port=8081）
  ④  SPRING_APPLICATION_JSON（环境变量形式的内联 JSON）
  ⑤  ServletConfig / ServletContext 初始化参数
  ⑥  JNDI（java:comp/env）
  ⑦  Java 系统属性（-Dserver.port=8081）
  ⑧  ★ 操作系统环境变量（SERVER_PORT=8081，Spring 自动做 relaxed binding）
  ⑨  random.*（RandomValuePropertySource）
  ⑩  打包 jar 外部的 profile 配置（application-prod.yml）
  ⑪  打包 jar 外部的 application.yml
  ⑫  打包 jar 内部的 profile 配置（classpath:application-prod.yml）
  ⑬  ★ 打包 jar 内部的 application.yml（本项目用的）
  ⑭  @PropertySource 注解（自定义配置文件）
  ⑮  默认属性（SpringApplication.setDefaultProperties）
  ⑯  配置树（config tree，K8s 挂载卷场景）
  ⑰  Docker secrets
```

**简化为五层记忆**：

```text
命令行（--xxx） > 环境变量（XXX=yyy） > 系统属性（-Dxxx） > 外部配置文件 > 内部配置文件 > 代码默认值
```

**Relaxed Binding（松散绑定）**：

```text
同一个配置，这些写法等价（Spring 自动映射）：
  application.yml:  server.port: 8081
  环境变量:         SERVER_PORT=8081
  命令行:           --server.port=8081
  系统属性:         -Dserver.port=8081

★ 所以环境变量名要大写下划线（SERVER_PORT），Spring 会归一化为 server.port
```

<a id="sb3-2"></a>
## 二、优先级实测：命令行覆盖 yml（真实启动）

**项目现状**：`application.yml` 里 `server.port: ${SERVER_PORT:8080}`（默认 8080）。

**实验**：用命令行参数覆盖成 8081，看谁赢。

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=lite" "-Dspring-boot.run.arguments=--server.port=8081"
```

**预期**（本课实测结果）：

```text
① 启动日志出现：Tomcat started on port 8081
② curl http://localhost:8081/api/request-pipline/create → 能通
③ curl http://localhost:8080 → 连不上（8080 没监听）

→ 结论：命令行参数（优先级③）覆盖了 application.yml（优先级⑬）
```

**另一个实验（环境变量 vs yml）**：

```powershell
$env:SERVER_PORT = "8082"
mvn spring-boot:run "-Dspring-boot.run.profiles=lite"
# → 8082 生效（环境变量⑧ 高于 yml⑬）
# 再叠加命令行：--server.port=8083 → 8083 赢（命令行③ 高于环境变量⑧）
```

**这三个实验就是"优先级矩阵"的活证据**。

**实测结果（2026-11-25）**：

```text
启动：mvn spring-boot:run "-Dspring-boot.run.profiles=lite" "-Dspring-boot.run.arguments=--server.port=8081"

① 启动成功（Started ResearchAgentApplication）
② 端口状态：
   8081 = ✅ 监听（Spring 用了命令行给的 8081）
   8080 = ✅ 未监听（yml 里的 8080 被覆盖，没有回退）
③ 接口验证：POST http://localhost:8081/api/request-pipline/create → HTTP 400（空 body 被校验拦，接口是通的）
→ 结论：命令行参数（优先级③）覆盖 application.yml（优先级⑬）——实测确认
```
<a id="sb3-3"></a>
## 三、Profile 组合（本项目 lite + mybatis 怎么叠加）

### 3.1 激活方式的优先级（同样是"谁赢"）

```text
高 →  命令行 --spring.profiles.active=redis
      环境变量 SPRING_PROFILES_ACTIVE=redis
      系统属性 -Dspring.profiles.active=redis
      yml 的 spring.profiles.active
低 →  代码 setAdditionalProfiles()
```

### 3.2 三种"组合"机制

```java
# ① active 列表（本项目 start.ps1 的做法）
spring.profiles.active: lite,mybatis

# ② group（Boot 2.4+，给"一组 profile 起个名"）
spring.profiles.group.dev = lite,mybatis
#   然后 --spring.profiles.active=dev 等价于 lite,mybatis

# ③ include（老机制，2.4+ 受限：只能 include 非 profile-specific 文件）
spring.profiles.include: base
```

**本项目的分层设计（正是 group 的手工版）**：

```text
application-lite.yml       基础层（H2 + 全关 + mock）
application-mybatis.yml    模块层（persistence=mybatis）
application-jpa.yml        模块层（persistence=jpa）
application-redis.yml      模块层（app.cache.enabled=true + cache.type=redis）
application-kafka.yml      模块层（listener.auto-startup=true）
application-llm.yml        模块层（client-impl=http + key）
application.yml            默认层（= cluster：PG + 全开）

start.ps1 -Mode mybatis   → SPRING_PROFILES_ACTIVE=lite,mybatis
start.ps1 -Mode redis,kafka → SPRING_PROFILES_ACTIVE=lite,redis,kafka
```

```text
★ 注意"覆盖 vs 合并"：
  同一个 key 在不同 profile 文件里 → 后加载的 profile 覆盖前者（列表按 active 顺序）
  ★ 但不同 key 是合并的（lite 的 h2 配置 + mybatis 的 persistence 配置共存）
  → 所以"模块 profile 只写自己那几个 key"是正确用法（不要重复写基础层的东西）
```

### 3.3 Profile-specific 文件的加载规则

```text
application-{profile}.yml 只在对应 profile 激活时加载
application.yml 永远加载（"兜底层"）
→ 所以基础配置放 application.yml 或 lite 层，模块配置放 application-{module}.yml
```

<a id="sb3-4"></a>
## 四、spring.config.import（多文件 / Nacos / 配置树）

```yaml
# ① 导入额外配置文件（Boot 2.4+，替代部分 include 用法）
spring:
  config:
    import: optional:classpath:extra.yml, optional:file:./config/local.yml

# ② 导入配置中心（本项目 Nacos 的写法）
spring:
  config:
    import: optional:nacos:${spring.application.name}.yml

# ③ 导入 config tree（K8s Secret/ConfigMap 挂载目录）
spring:
  config:
    import: optional:configtree:/etc/secrets/
```

```text
★ optional: 前缀的含义：导入失败不报错（配置中心没起也能启动）
  → 本项目用 optional:nacos:... 正是为了"Nacos 挂了也能本地起"
★ 与 import-check：Boot 2.4+ 对 import 有校验（import-check.enabled=false 可关，
  项目 lite 里 `spring.cloud.nacos.config.import-check.enabled: false` 就是它）
```

<a id="sb3-5"></a>
## 五、配置加密（三种方案与本项目做法）

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 环境变量注入**（本项目） | `api-key: ${OPENAI_API_KEY}` + 启动脚本 export | 零依赖、最简单 | 密钥在进程环境可见（ps/proc） |
| **B. jasypt 加密** | `ENC(密文)` + 主密码（启动时给） | 配置文件里是密文（可入库） | 主密码还是要给（换个地方存） |
| **C. 密钥管理服务** | Vault / K8s Secret / 云 KMS | 真正安全（审计/轮换） | 基础设施成本 |

```yaml
# B 方案示例（jasypt-spring-boot-starter）
app:
  secret:
    key: ENC(3f8a...=)          # 密文
# 启动：-Djasypt.encryptor.password=主密码（或环境变量 JASYPT_ENCRYPTOR_PASSWORD）
```

**本项目的做法（A 方案）**：

```text
start.ps1：
  $env:OPENAI_API_KEY = "sk-..."              ← 环境变量（明文在脚本里，负责人兜底）
application-llm.yml：
  api-key: ${OPENAI_API_KEY:sk-...}          ← 环境变量优先，缺省用兜底
docker/.env：
  OPENAI_API_KEY=sk-...                       ← compose 读取（容器环境变量）
```

```text
★ 结论：项目用"环境变量 + 兜底"（A），适合开发/学习；
  生产建议 C（K8s Secret / Vault），至少 B（密文入库）。
```

<a id="sb3-6"></a>
## 六、配置刷新（@RefreshScope 与限制）

```text
原生 Spring Boot：【不支持】运行时改配置自动生效（要重启）
  → 配置在启动时绑定（@ConfigurationProperties 绑定后是普通对象）

Spring Cloud Context 提供：
  @RefreshScope（Bean 级刷新）+ POST /actuator/refresh（触发刷新）
  → 配合配置中心（Nacos/Config Server）实现"改配置 → 刷新 → 生效"

★ 注意：
  ① @RefreshScope 只能刷新它标记的 Bean（不是全部）
  ② @Value 注入的值不会自动刷新（要 @RefreshScope 或 @ConfigurationProperties）
  ③ 本项目没用配置中心刷新（Nacos 只做了 optional 导入）
```

<a id="sb3-7"></a>
## 七、本项目实践：start.ps1 的 env 注入属于哪一层

**看 start.ps1 的几行**：

```powershell
$env:SPRING_PROFILES_ACTIVE = $profileLine        # ← 层⑧ 环境变量
$env:APP_PERSISTENCE_MODE = $persistenceMode      # ← 层⑧ 环境变量
$env:JAVA_TOOL_OPTIONS = "-Dspring.cloud.nacos..."# ← 层⑦ 系统属性（JVM 参数）
mvn spring-boot:run "-Dspring-boot.run.profiles=$profileLine"   # ← 层③ 命令行
```

**所以项目里"同一件事"有三条路径**：

```text
① mvn 的 -Dspring-boot.run.profiles  = 命令行参数（最高）
② $env:XXX                          = 环境变量
③ JAVA_TOOL_OPTIONS 里的 -D         = 系统属性
```

```text
★ 为什么 start.ps1 用 env 而不是全塞命令行？
  ① 环境变量对所有子进程生效（mvn → fork 的 java 都能读到）
  ② 命令行参数只对当次执行
  ③ JAVA_TOOL_OPTIONS 是 JVM 级（对所有 Java 进程生效——注意污染）
★ 排障顺序：先怀疑命令行（最高）→ 环境变量 → yml
```

<a id="sb3-8"></a>
## 八、自测题（答案）

### Q1：同一个 key 在命令行、环境变量、application.yml 都有，谁赢？为什么？

**答案**：命令行赢（优先级③ vs 环境变量⑧ vs yml⑬）。

```text
机制：Spring 的 Environment 是多源组合（MutablePropertySources），按顺序遍历，
     第一个有值的源返回结果（"谁在前谁赢"）
验证：SB3 实测（--server.port=8081 覆盖 yml 的 8080）
```

### Q2：环境变量名为什么是大写下划线（SERVER_PORT）？和 server.port 怎么对上？

**答案**：Relaxed Binding（松散绑定）。

```text
规则：环境变量 SERVER_PORT = server.port / server-port / serverPort 都等价
     Spring 归一化：大写 → 小写、下划线 → 点
注意：全大写是"惯例"不是强制（但 Windows 环境变量大小写不敏感、Linux 敏感，
     统一大写下划线最稳）
```

### Q3：`spring.profiles.group` 与 `spring.profiles.active` 什么关系？

**答案**：group 是"给一组 profile 起别名"，active 是"激活哪些"。

```yaml
spring.profiles.group.dev = lite,mybatis
# 然后 active=dev 等价于 active=lite,mybatis
```

```text
用途：项目里常用"组合名"（dev/prod）而不是记一堆模块名
注意：group 是 Boot 2.4+ 的特性（老版本用 include，且有 load 顺序坑）
```

### Q4：`spring.config.import: optional:...` 的 optional 有什么用？

**答案**：导入失败不报错（配置中心/外部文件缺失也能启动）。

```text
本项目：spring.config.import: optional:nacos:... 
  → Nacos 没起时不崩（配合 import-check.enabled=false）
★ 不加 optional → 导入失败 = 启动失败（配置中心必须是强依赖时用）
```

### Q5：为什么 @Value 改了配置不会自动生效？@RefreshScope 解决什么？

**答案**：

```text
@Value 在 Bean 创建时绑定一个值（注入后就是普通字段）→ 改配置源不会回填
@RefreshScope = 重新创建该 Bean（销毁旧实例 → 用新配置重建）
  → 但只对该 Bean 生效，且需要 Spring Cloud Context + /actuator/refresh 触发
★ 原生 Boot 不支持运行时刷新（要重启）
```

### Q6：生产环境密钥怎么放？项目现在的做法有什么问题？

**答案**：

```text
生产推荐：K8s Secret / Vault / 云 KMS（C 方案）→ 审计、轮换、不进仓库
          至少 jasypt 密文入库（B 方案）

项目现状：明文写在 start.ps1 / application-llm.yml 的兜底值里（A 方案）
问题：① 明文进仓库（泄露风险）② 进程环境可见 ③ 轮换要改代码
结论：开发可接受（负责人兜底），生产必须换 C（或至少 B）
```

<a id="sb3-9"></a>
## 九、一句话

```text
配置优先级五层：命令行 > 环境变量 > 系统属性 > 外部文件 > 内部文件 > 默认值。
Profile 是"分层覆盖"（同 key 覆盖、不同 key 合并），group 是给分层组起名。
排障先问"谁给的"：--debug 里看 Environment 的 propertySources 顺序，或
启动日志的 "The following N profiles are active" + 各源覆盖记录。
```
