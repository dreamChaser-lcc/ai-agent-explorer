# SP6 · 配置体系与 Profile

> **课程**：Spring 基础实战 · 模块二（2026-10-21）
> **已会**：SP5 的 `@ConfigurationProperties`（mini-starter）、场景⑧ 的 `APP_LLM_*` 环境变量、`application-dev.yml`
> **本次**：**配置优先级全景**（命令行 / 环境变量 / profile yml / 主 yml 谁赢）、**Profile 机制**（active / group / on-profile）、`@ConfigurationProperties` 绑定规则系统化
> **对标**：廖雪峰 23.5 / 23.7
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **SP5**：`@ConfigurationProperties(prefix="app.greeting")` + `@EnableConfigurationProperties` 把 yml 绑定成对象；
- **场景⑧**：真实启动时用 `APP_LLM_API_KEY` / `JAVA_TOOL_OPTIONS` 等环境变量覆盖配置；
- **本项目现状**：`application.yml`（主配置，默认 PG）+ `application-dev.yml`（dev 覆盖为 H2、禁用 Flyway）。

---

## 一、配置优先级全景：同一个 key 有 5 个地方写了，听谁的？

Spring Boot 3 的配置源有十几级，**开发时最需要记住的是前五层**（从高到低）：

| 优先级 | 来源 | 写法示例 | 典型用途 |
|---|---|---|---|
| **1（最高）** | **命令行参数** | `--sp6.demo.name=from-cmd` | 临时调试、容器启动参数 |
| **2** | Java 系统属性 | `-Dsp6.demo.name=from-sys` | JVM 启动参数（`JAVA_TOOL_OPTIONS`） |
| **3** | **操作系统环境变量** | `SP6_DEMO_NAME=from-env` | 生产密钥、集群差异化配置 |
| 4 | `application-{profile}.yml` | `application-dev.yml` | 环境专属配置 |
| 5 | `application.yml` | 主配置 | 公共默认值 |
| 6 | `@PropertySource` | 注解导入 | 遗留项目 |
| 7（最低） | 代码默认值 | 字段初始值 / `setDefaultProperties` | 兜底 |

**环境变量的命名转换**（Spring 的"松散绑定"）：

```text
sp6.demo.name   →  SP6_DEMO_NAME    （点 → 下划线，全大写）
sp6.demo.user-name → SP6_DEMO_USERNAME（短横线也变下划线）
```

**核心记忆**：**外部 > 内部，具体 > 通用**——命令行最"外"、最临时，所以最高；jar 内 yml 最"内"，所以低。

---

## 二、Profile 机制：一套代码，多套配置

### 2.1 三种用法

| 用法 | 写法 | 说明 |
|---|---|---|
| **激活** | `spring.profiles.active: dev`（或 `-Dspring-boot.run.profiles=dev`） | 决定加载哪个 `application-{profile}.yml` |
| **分组** | `spring.profiles.group.prod: [prod-db, prod-cache]` | 一次激活一组 profile（3.0+ 推荐，替代 include） |
| **文件内条件** | `spring.config.activate.on-profile: dev` | 同一个 yml 里按 profile 分段（多文档 yml 用 `---` 分隔） |

### 2.2 本项目的实际用法

```text
application.yml       → 公共配置 + 生产默认（PG、Flyway 开）
application-dev.yml   → dev 覆盖（H2、Flyway 关、schema-dev.sql）
启动参数              → -Dspring-boot.run.profiles=dev
```

### 2.3 常见坑

| 坑 | 说明 |
|---|---|
| profile 拼错 | `dev` 写成 `Dev` → 静默不生效，用默认配置（我们的 PG/Flyway 事故就是配置源叠加的变种） |
| 环境变量能"越级" | 环境变量优先级高于 yml——**你以为 yml 改了，其实环境变量盖着**（排查配置不生效的第一步：查环境变量） |
| 远端配置中心 | Nacos/Config Server 的配置优先级**高于本地 yml**（场景⑧ 实测：远程集群配置把 dev 的 H2 覆盖成 PG） |

---

## 三、`@ConfigurationProperties` 绑定规则系统化

| 能力 | 写法 | 说明 |
|---|---|---|
| 松散绑定 | `sp6.demo.user-name` / `userName` / `USER_NAME` | 都绑到 `userName` 字段 |
| 默认值 | 字段初始化 `= "default"` | yml 没配就用它 |
| 集合 | `sp6.demo.tags: [a, b]` 或 `tags[0]: a` | `List<String>` 字段 |
| Map | `sp6.demo.map.k1: v1` | `Map<String, String>` 字段 |
| 嵌套对象 | `sp6.demo.db.host: x` | 内部类字段 + getter/setter |
| 校验 | `@Validated` + `@NotBlank` | 启动时校验，不合法直接失败 |
| 构造器绑定 | record | Spring Boot 3 支持，字段不可变 |

**边界**：`@ConfigurationProperties` 适合**成组配置**（一个前缀下多个关联项）；`@Value("${key}")` 适合**单个值**（且不支持松散绑定、不支持校验、不支持热刷新）。
---

## 四、★ 实验：配置优先级四级实测

### 4.1 设计

`sp6.demo.name` 在四个地方被设置：

```text
主 yml（application.yml）      sp6.demo.name: from-main-yml
dev yml（application-dev.yml） sp6.demo.name: from-dev-yml
环境变量                        SP6_DEMO_NAME=from-env
系统属性（JVM -D）              -Dsp6.demo.name=from-sys
```

### 4.2 实测结果（2026-10-21 三幕全绿）

| 幕 | 额外设置 | 实测 `[SP6]` 日志 | 判定 |
|---|---|---|---|
| **①** | 只有两个 yml（profile=dev） | `name=from-dev-yml，source=dev-yml，tags=[dev]` | **profile yml > 主 yml** |
| **②** | `SP6_DEMO_NAME=from-env`、`SP6_DEMO_SOURCE=env` | `name=from-env，source=env，tags=[dev]` | **环境变量 > profile yml** |
| **③** | 再加 `-Dsp6.demo.name=from-sys -Dsp6.demo.source=sys` | `name=from-sys，source=sys，tags=[dev]` | **系统属性 > 环境变量** |

**一个重要细节**：三幕里 `tags` **始终是 `[dev]`**——环境变量和系统属性只覆盖它们**显式设置**的 key，其余配置仍来自 yml。**覆盖是按 key 独立的，不会把整组配置冲掉。**

### 4.3 命令行层级（优先级 1）未实跑

`--sp6.demo.name=from-cmd` 理论最高，本次因工具链传参问题（带空格的 `spring-boot.run.arguments` 被拆开）未能实跑。你在自己终端里可以直接验证：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--sp6.demo.name=from-cmd"
```

---

## 五、Profile 进阶：分组与文件内条件

### 5.1 分组（一次激活多个 profile）

```yaml
spring:
  profiles:
    group:
      prod: [prod-db, prod-cache]   # 激活 prod 时，自动带上这两个
```

启动 `--spring.profiles.active=prod` → 实际激活 `prod`、`prod-db`、`prod-cache` 三个。

### 5.2 文件内条件（一个 yml 管多环境）

```yaml
# application.yml
spring:
  application:
    name: research-agent-backend

---
spring:
  config:
    activate:
      on-profile: dev      # 以下配置只在 dev 生效
  datasource:
    url: jdbc:h2:...

---
spring:
  config:
    activate:
      on-profile: prod     # 以下配置只在 prod 生效
  datasource:
    url: jdbc:postgresql:...
```

`---` 分隔多个文档，靠 `on-profile` 切换——适合**配置不多**的项目；配置复杂时仍建议分文件（`application-{profile}.yml`）。

---

## 六、项目关联地图

| 概念 | 项目位置 |
|---|---|
| 主配置 | `application.yml`（PG 默认、Flyway 开） |
| dev 覆盖 | `application-dev.yml`（H2、Flyway 关、schema-dev.sql） |
| 环境变量覆盖 | `APP_LLM_*` / `OPENAI_API_KEY`（场景⑧） |
| 系统属性覆盖 | `JAVA_TOOL_OPTIONS` 里的 `-Dspring.cloud.nacos.config.enabled=false` |
| 远端配置中心 | Nacos（优先级高于本地 yml，场景⑧ 踩过） |
| 本课演示 | `sp6.demo.*`（代码 + 两个 yml 的"教学演示"段，可删） |

---

## 七、思考题（自查 + 参考答案）

1. 同一个 key 在 主 yml / dev yml / 环境变量 都写了，谁生效？<br>
   **参考答案**：**环境变量**（优先级 3）> dev yml（4）> 主 yml（5）。实测三幕已证明（`from-env` 赢 `from-dev-yml`）。
2. 为什么环境变量名要写成 `SP6_DEMO_NAME` 这种全大写下划线？<br>
   **参考答案**：操作系统环境变量只能用大写字母+下划线（惯例 + 兼容性）；Spring 的松散绑定会自动把 `sp6.demo.name` 映射到 `SP6_DEMO_NAME`（点/短横线 → 下划线，转大写）。
3. `@Value` 和 `@ConfigurationProperties` 怎么选？<br>
   **参考答案**：**成组配置**（一个前缀下一组相关项）用 `@ConfigurationProperties`——支持松散绑定、集合/Map/嵌套对象、校验、构造器绑定；**单个值**（如一个开关）用 `@Value` 更轻。
4. 生产环境要改一个配置，不想重新打包，有几种办法？<br>
   **参考答案**：① 环境变量（容器/服务器设置）；② 命令行参数（启动脚本加 `--key=value`）；③ 配置中心（Nacos/Config Server，改完刷新）。三者优先级都高于 jar 内 yml——**"配置外置"正是靠优先级设计实现的**。

---

## 八、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| [`Sp6DemoProperties.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/Sp6DemoProperties.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/Sp6DemoProperties.java) | `sp6.demo.*` 配置属性（name/source/tags） | ✅ 可删 |
| [`Sp6DemoConfiguration.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/Sp6DemoConfiguration.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/Sp6DemoConfiguration.java) | 启用配置绑定 | ✅ 可删 |
| [`Sp6DemoRunner.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/Sp6DemoRunner.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/Sp6DemoRunner.java) | 启动后打印最终取值 | ✅ 可删 |
| `application.yml` 的「SP6 教学演示」段 | 主 yml 里 `from-main-yml` | ✅ 可删（有注释标记） |
| `application-dev.yml` 的「SP6 教学演示」段 | dev yml 里 `from-dev-yml` | ✅ 可删（有注释标记） |

---

## 九、验证记录（2026-10-21）

| 验证项 | 结果 | 判定 |
|---|---|---|
| profile yml 覆盖主 yml | `name=from-dev-yml`、`tags=[dev]` | ✅ |
| 环境变量覆盖 profile yml | `name=from-env`、`source=env`、`tags` 仍 `[dev]` | ✅ 按 key 独立覆盖 |
| 系统属性覆盖环境变量 | `name=from-sys`、`source=sys`、`tags` 仍 `[dev]` | ✅ |
| 命令行层级 | 未实跑（工具传参限制），讲义给出终端命令 | ⚠️ 学员可自测 |

---

## 十、本课问答记录（2026-10-21）

| # | 提问（原话） | 落档位置 |
|---|---|---|
| **Q1** | "com.aiexplorer 下有两个应用可以这样理解吗？为什么它的结构不像 micro-lab 一样微服务多个应用" | §10.1「项目结构：目录 ≠ 模块 ≠ 应用」（见下，含 pom/main 实测） |
| **Q2** | "在真实项目中会有兄弟包这种情况吗，还是只会出现一个应用只会出现一个主包代码才是对的" | §10.2「兄弟包是常态，主包唯一是假规则」（见下） |
| **Q3** | "所以读取配置要 @ConfigurationProperties 和 @Value 这两个注解读取是吗，给我详细讲解他们的区别" | §10.3「@Value vs @ConfigurationProperties：12 维对比 + 决策表」（见下） |
| **Q4** | "@Validated + @NotBlank 这两个校验是要结合 @ConfigurationProperties 才能使用的吗，分别说下他们有什么作用" | §10.4「@Validated（执行人）+ @NotBlank（说明书）：四个使用场景」（见下） |

### 10.1 项目结构：目录 ≠ 模块 ≠ 应用（Q1 · 2026-10-25）

#### 一、实测证据

```text
① Maven 模块（看 pom.xml）：
   backend\pom.xml                        ← 1 个（单模块）
   micro-lab\pom.xml + gateway/order/user ← 4 个（父 pom + 3 子模块）

② 启动类（有 main 的）：只有 1 个 ResearchAgentApplication

③ starterdemo 的 3 个类：
   GreetingAutoConfiguration / GreetingProperties / GreetingService
   —— 无 main、无 pom、不是应用
```

#### 二、三层概念

| 层级 | 判定标准 |
|---|---|
| **应用** | `pom.xml` + `main` 方法 + 能独立启动 |
| **模块** | 有 `pom.xml`（可独立打包/被依赖） |
| **包** | 只是目录，**不构成独立单元** |

**`com.aiexplorer` 下两个目录都是"包"**：

```text
backend/pom.xml                    ← 唯一模块
  └── src/main/java/com/aiexplorer/
        ├── researchagent/         ← 包（主应用代码，含唯一 main）
        └── starterdemo/           ← 包（SP5 的"假第三方库"，3 个类无 main）
```

#### 三、starterdemo 的定位

```text
模拟第三方库：
  · 有自己的 imports 清单（resources/META-INF/spring/...）
  · 包名故意放 com.aiexplorer.starterdemo（兄弟包）
  · 应用的 @ComponentScan 扫不到 → 只能靠 imports 加载（行为与真实第三方库等价）

为什么放兄弟包：放 com.aiexplorer.researchagent.starterdemo 会被扫到，证明不了清单的作用。
```

#### 四、micro-lab vs research-agent

| | micro-lab | research-agent |
|---|---|---|
| Maven 结构 | **多模块**（父 + 3 子） | **单模块**（1 个 pom） |
| 应用数 | **3 个**（gateway/order/user） | **1 个** |
| 端口 | 各独立（8081/8082/8083） | 8080 |
| 服务调用 | OpenFeign | 进程内注入 |
| 配置 | 每服务一套 + Nacos | 一套 yml |
| 部署 | 3 个 Pod | 1 个 Pod |

#### 五、为什么没拆微服务

| 原因 | 说明 |
|---|---|
| 单体更简单 | 事务/锁/清理/N+1 在单体下设计更直接 |
| 拆了要配套 | Nacos 注册、Feign、网关、分布式事务（T12 才学） |
| 教学目的不同 | micro-lab 是微服务沙盘；research-agent 是业务场景沙盘 |
| 循序渐进 | 先懂单实例方案（Redis 锁/@Scheduled），再学平台化方案 |

#### 六、真要拆微服务的样子

```text
research-agent/
  ├── pom.xml                 ← 父 pom
  ├── agent-common/           ← DTO/工具
  ├── agent-task-service/     ← 任务服务（①-④）
  ├── agent-executor-service/ ← 执行服务（⑥-⑧）
  └── agent-report-service/   ← 报告服务（⑦）
```

拆完必须解决的问题：Nacos 服务发现 / Feign 调用 / **跨服务事务不能用 @Transactional** / Nacos 配置中心 / traceId 透传。

#### 七、与 SP6 配置体系的关系

| | 单体（本项目） | 微服务（micro-lab） |
|---|---|---|
| 配置文件 | 1 套（application.yml + dev） | 每服务一套 |
| 配置中心 | 无（nacos config 依赖存在但禁用） | Nacos 统一管理 + 热更新 |
| 优先级 | 命令行 > 系统属性 > 环境变量 > profile yml > 主 yml | 同上 + **配置中心优先级更高** |

#### 八、一句话

```text
目录 ≠ 应用。判断应用只看两样：pom.xml + main 方法。
researchagent / starterdemo 都是包；micro-lab 的多应用来自"多 Maven 模块"。
```

### 10.2 兄弟包是常态，主包唯一是假规则（Q2 · 2026-10-25）

#### 一、规范里的铁证

live-java 规范原文：

```java
catch (BusinessException | com.basic.common.infrastructure.exception.BusinessException e) { ... }
```

`com.vzan.common.exception.BusinessException` 和 `com.basic.common.infrastructure.exception.BusinessException` 是**两套兄弟包**（`com.vzan` 与 `com.basic`）共存于一个应用——并购/历史遗留。规范要专门写"两个都要 catch"。

#### 二、兄弟包的四种真实场景

| 场景 | 例子 | 说明 |
|---|---|---|
| ① 多公司/多团队包共存 | `com.vzan.*` + `com.basic.*` + `com.njyqkj.*` | 并购/历史遗留 |
| ② 公共库 vs 业务应用 | `com.company.common` + `com.company.order` | 公共模块被多应用依赖 |
| ③ 第三方库的包 | `org.springframework.*`、`dev.langchain4j.*` | **所有 jar 的包都是兄弟包** |
| ④ 子包（不是兄弟包） | `com.company.order.api` | 同根下，正常分层 |

**关键**：任何应用本来就与几十个第三方包共存——**"一个应用一个顶层包"从来就不成立**。

#### 三、唯一硬约束

| 规则 | 实际情况 |
|---|---|
| 启动类所在包 = `@ComponentScan` 默认根 | ✅ 唯一硬约束 |
| 业务代码建议放启动类包及子包 | ✅ 方便但不强制 |
| 兄弟包可显式 `@ComponentScan` 纳入 | ✅ |
| 兄弟包可不扫描（靠 imports/手动注册） | ✅ |
| **"只能有一个顶层包"** | ❌ **不存在这条规则** |

#### 四、为什么看起来只有一个主包

```text
com.mycompany.myapp
  ├── controller/ service/ repository/
```

原因不是规则，而是**"这样最省事"**：默认扫描范围正好覆盖，一行配置都不用写。

#### 五、兄弟包三种处理方式

| 情况 | 做法 |
|---|---|
| 兄弟包里有你的 `@Component` | 显式 `@ComponentScan(basePackages = {...})` |
| 兄弟包是第三方库（有 imports 清单） | **不用管**（自动配置加载） |
| 兄弟包是第三方库（无 imports） | `@Import` 手动引入（不推荐） |

#### 六、真实多模块项目

```text
company-project/
  ├── pom.xml（父）
  ├── common/            ← com.company.common
  ├── order-service/     ← com.company.order
  └── user-service/      ← com.company.user
```

```java
package com.company.order;

@SpringBootApplication
@ComponentScan(basePackages = {"com.company.order", "com.company.common"})  // ★ 扫兄弟包
public class OrderApplication { }
```

**必须扫 common 的原因**：它是另一个顶层包，里面有全局异常处理器/工具 bean——不扫注入不到。

#### 七、SP5 的 starterdemo 属于哪种

```text
模拟"场景 ②"（公共库 vs 业务应用）：
  真实项目 → 公共库在独立 jar
  我们的模拟 → 同模块兄弟包（省发布会麻烦）

它的特殊之处不是"兄弟包"（很常见），
而是"故意不扫描 + 靠 imports 加载"——为演示自动配置原理。
真实项目不会把第三方库源码放进自己项目（starterdemo 是教学产物，可删）。
```

#### 八、一句话

```text
兄弟包 = 常态（第三方库本来就是几十个兄弟包）
主包唯一 = 假规则（只是"最省事"的默认写法）

真正的约束只有一条：@ComponentScan 默认只扫启动类所在包及子包
  → 兄弟包有 @Component → 显式加 @ComponentScan
  → 兄弟包是第三方库 → 靠 imports 清单，不用管
```

### 10.3 @Value vs @ConfigurationProperties（Q3 · 2026-10-25）

#### 一、两种主要方式 + 第三种

| 方式 | 形态 | 适用 |
|---|---|---|
| **`@Value`** | 字段/参数上一个注解读**一个值** | 1-2 个简单值 |
| **`@ConfigurationProperties`** | 一个类绑**一组值**（前缀下所有 key） | 成组配置（99%） |
| `Environment` | `env.getProperty("key")` | 运行时才知道 key |

#### 二、同一配置的两种写法

配置：

```yaml
app:
  llm:
    base-url: https://api.example.com
    timeout-seconds: 30
    max-retries: 2
```

写法 A（@Value，分散）：

```java
@Component
public class LlmClient {
    @Value("${app.llm.base-url}")            private String baseUrl;
    @Value("${app.llm.timeout-seconds:30}")  private int timeoutSeconds;   // :30 默认值
    @Value("${app.llm.max-retries:2}")       private int maxRetries;
}
```

写法 B（@ConfigurationProperties，集中）：

```java
@ConfigurationProperties(prefix = "app.llm")
@Validated                                        // 开启校验
public class LlmProperties {
    @NotBlank private String baseUrl;             // 为空则启动失败
    private int timeoutSeconds = 30;              // 默认值写字段上
    private int maxRetries = 2;
    private Retry retry = new Retry();            // 嵌套对象
}
```

#### 三、12 维对比

| # | 维度 | @Value | @ConfigurationProperties |
|---|---|---|---|
| 1 | 粒度 | 一个值 | 一组值 |
| 2 | **松散绑定** | ❌ 必须精确 | ✅ base-url = baseUrl |
| 3 | 默认值 | `${key:默认}` | 字段初始值 |
| 4 | 嵌套对象 | ❌ | ✅ |
| 5 | 集合/Map | ⚠️ SpEL 麻烦 | ✅ 原生 |
| 6 | 枚举 | ⚠️ 仅字符串 | ✅ 自动转换 |
| 7 | **校验** | ❌ 不生效 | ✅ @Validated + JSR-303 |
| 8 | **热更新** | ❌ 需 @RefreshScope | ✅ 可配合刷新 |
| 9 | SpEL | ✅ `#{2*3}` | ❌ |
| 10 | IDE 提示 | ❌ | ✅（metadata） |
| 11 | 集中管理 | ❌ 散落 | ✅ 一个类看全 |
| 12 | 注册 | @Component 即可 | @Component / @EnableConfigurationProperties / @ConfigurationPropertiesScan |

#### 四、决策表

| 场景 | 选择 |
|---|---|
| 1 个开关 | `@Value` |
| 一组配置（LLM/Kafka/DB） | **@ConfigurationProperties** |
| 需要校验 | **@ConfigurationProperties** |
| 需要嵌套/集合/枚举 | **@ConfigurationProperties** |
| 需要 SpEL | `@Value`（唯一优势） |
| 运行时才知道 key | `Environment` |

**一句话**：能后者就后者；`@Value` 只留"单值 + 简单类型"。

#### 五、企业规范为什么慎用 @Value

live-java 规范原文：**"尽量不用 `@Value`，Nacos 修改后无法热更新，统一 `@ConfigurationProperties`"**。

| # | 问题 | 后果 |
|---|---|---|
| 1 | 无法热更新 | Nacos 改了值不变（要重启） |
| 2 | 无法校验 | 配错启动不报 → 运行时 NPE |
| 3 | 无法集中管理 | 100 个 @Value 散落，改前缀要全局搜 |
| 4 | 无 IDE 提示 | 手打 key 靠记忆 |

#### 六、项目实测

```text
@Value                      → 1 处（BeanLifecycleDemo，SP1 教学演示）
@ConfigurationProperties    → 5 处（LlmProperties / ExecutionProperties / CleanupProperties / Sp6DemoProperties / GreetingProperties）
@Validated                  → 0 处（★ 校验还没用上）
```

**已基本遵循规范**（几乎不用 @Value），**但缺校验**：

```java
@ConfigurationProperties(prefix = "app.llm")
@Validated                                          // ← 补这个
public class LlmProperties {
    @NotBlank private String baseUrl;               // ← 和这个
    @Min(1) @Max(300) private int timeoutSeconds;
}
```

效果：配错 → **启动直接失败并说明原因**（比运行时 NPE 好一万倍）。半小时可补。

#### 七、五个坑

| 坑 | 现象 | 解法 |
|---|---|---|
| @Value key 写错 | `Could not resolve placeholder` | 加默认值 `${key:default}` |
| @Value 用 kebab、字段 camel | 匹配不上（不支持松散绑定） | 精确写 key |
| @ConfigurationProperties 未注册 | 注入为 null | @Component / @EnableConfigurationProperties |
| 校验不生效 | 忘 @Validated | 类上加 |
| yml 中文乱码 | 编码不一致 | 统一 UTF-8 |

#### 八、一句话

```text
@Value = "读一个值"（简单但没校验没热更新）
@ConfigurationProperties = "读一组值"（集中、可校验、可嵌套、可热更新）
```

### 10.4 @Validated（执行人）与 @NotBlank（说明书）（Q4 · 2026-10-25）

#### 一、不是必须结合 @ConfigurationProperties

Bean Validation（JSR-380）是通用机制，有**四个**使用场景（关键看**加在哪**）：

| # | 场景 | 触发器 | **加在哪** | 项目现状 |
|---|---|---|---|---|
| 1 | Controller `@RequestBody` | **`@Valid`**（`@Validated` 也行） | **方法参数上** | ✅ 2 处（createTask/confirmPlan） |
| 2 | Controller `@RequestParam` | **`@Validated`** | **类上** | ❌ 未用（`@Valid` 对基本类型无效） |
| 3 | `@ConfigurationProperties` | **`@Validated`** | **类上** | ❌ 未用（0 处；`@Valid` 不能标类上） |
| 4 | Service 方法参数 | **`@Validated`** | **类上** | ❌ 未用 |

**记忆口诀**：

```text
校验"一个对象"        → @Valid 放参数上（标准写法）
校验"零散参数/类字段" → @Validated 放类上
```

**两者能否互换**：

| 场景 | `@Valid` | `@Validated` |
|---|---|---|
| `@RequestBody` 参数 | ✅ | ✅ **都行** |
| `@RequestParam` 参数 | ❌ 无效 | ✅（类上） |
| `@ConfigurationProperties` | ❌ 不能标类上 | ✅ |
| Service 方法 | ❌ | ✅ |

**`@Validated` 是 `@Valid` 的超集**（多了分组校验 + 类级能力）；给 `@RequestBody` 加校验时两者等价。

#### 二、两个注解的比喻

**`@NotBlank` = 说明书**（自己什么都不做）：

```java
public record CreateTaskRequest(
        @NotBlank(message = "标题不能为空") String title,   // ← 贴标签
        @NotNull ExecutionMode executionMode) {}
```

同类约束：`@NotNull` / `@NotEmpty` / `@Min` / `@Max` / `@Size` / `@Pattern` / `@Email`。

**三个易混的**：

| 注解 | `null` | `""` | `"   "` |
|---|---|---|---|
| `@NotNull` | ❌ | ✅ | ✅ |
| `@NotEmpty` | ❌ | ❌ | ✅ |
| `@NotBlank` | ❌ | ❌ | ❌（专治全空白） |

**`@Validated` = 执行人**（没有它说明书没人看）：

```java
@ConfigurationProperties(prefix = "app.llm")
@Validated                                        // ← 请检查这个类上的说明书
public class LlmProperties {
    @NotBlank private String baseUrl;
}
```

#### 三、谁触发谁

| 校验对象 | 必须加 | 加在哪 |
|---|---|---|
| `@RequestBody` 的 DTO | **`@Valid`** | 方法参数上 |
| `@RequestParam` 单个参数 | **`@Validated`** | **类上** |
| `@ConfigurationProperties` | **`@Validated`** | **类上** |
| Service 方法参数 | **`@Validated`** | **类上** |

**为什么 @RequestParam 必须类级**：`@Valid` 只作用于对象（级联校验字段），管不了零散基本类型参数。

#### 四、@Valid vs @Validated

| | `@Valid` | `@Validated` |
|---|---|---|
| 来源 | JSR-303 标准 | Spring 扩展 |
| 分组校验 | ❌ | ✅ |
| 能标类上触发方法参数校验 | ❌ | ✅ |
| 典型用法 | `@Valid @RequestBody` | 类上 `@Validated` |

#### 五、三个场景的失败表现

| 场景 | 失败异常 | 默认结果 |
|---|---|---|
| `@RequestBody` | `MethodArgumentNotValidException` | 400（项目已有全局处理） |
| `@RequestParam` | `ConstraintViolationException` | **500**（项目未处理！） |
| 配置类 | `ConfigurationPropertiesBindException` | **启动失败**（最好的失败方式） |

#### 六、项目实测

```text
spring-boot-starter-validation  ✅ 已引入
@Valid                          2 处
@NotBlank 3 / @NotNull 3 / @Size 1
@Validated                      0 处 ★
@Min / @Max / @Pattern          0 处
```

**现状**：`@RequestBody` 校验已生效；配置类校验、`@RequestParam` 校验未启用。

#### 七、不写触发器的后果

| 场景 | 后果 |
|---|---|
| `@RequestBody` 没 `@Valid` | 空标题能创建任务 |
| `@RequestParam` 没类级 `@Validated` | `size=100000` 能进服务 |
| 配置类没 `@Validated` | `batch-size: -1` 也能启动 |

#### 八、一句话

```text
@NotBlank  = 说明书（字段不能空）—— 自己不做任何事
@Validated = 执行人（请检查说明书）—— 没有它说明书没人看
@Valid     = 标准版执行人（用于 @RequestBody 对象）

配置类校验 = @ConfigurationProperties + @Validated + 字段约束
```
