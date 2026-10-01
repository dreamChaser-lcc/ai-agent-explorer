# Spring Boot 配置读取方式全解

> **定位**：把"配置从哪来（**书写**）→ 怎么进应用（**读取**）"讲全，并按三大场景横向对比：**本地开发 / K8s 部署 / Nacos 配置中心**。
> **配套**：《micro-lab配置文件全景讲解.md》（五类文件地图 + 优先级栈）、《backend接入集群实战复盘.md》（真实项目落地）。
> 代码样本取自本项目 backend（`LlmProperties` / `ExecutionProperties` / `AsyncExecutionConfiguration` 等真实文件）。

---

## 📖 目录

- [一、一张总图：配置的一生](#一一 张总图配置的一生)
- [二、书写全谱：配置的 6 种来源](#二书写全谱配置的-6-种来源)
- [三、读取全谱：代码侧的 4 种方式](#三读取全谱代码侧的-4-种方式)
- [四、三场景横向对比（核心）](#四三场景横向对比核心)
- [五、交叉速查（同一键的三写法 / 改值生效决策树 / 方式×场景矩阵）](#五交叉速查)
- [六、实战范例：一个键的三场景旅程](#六实战范例一个键的三场景旅程)
- [七、常见坑清单](#七常见坑清单)
- [八、企业实践速览：什么配置该放哪](#八企业实践速览什么配置该放哪)

---

## 一、一张总图：配置的一生

```
【书写侧：来源】                     【登记】                【读取侧：方式】
  ① jar 内 application.yml  ─┐
  ② jar 外 application.yml  ─┤
  ③ Nacos 配置中心           ─┼─→  Environment 配置栈  ─→  ① Environment API
  ④ OS 环境变量 / K8s env    ─┤    （按优先级叠加）         ② @Value
  ⑤ Java 系统属性 (-D)        ─┤                             ③ @ConfigurationProperties
  ⑥ 命令行参数 (--)          ─┘                             ④ @PropertySource / Binder
```

**两句话记住**：

1. **"来源"只负责往栈里加层**（谁也没"改"谁的文件）；
2. **"读取"永远只做一件事：向栈要值**——从栈顶往下找、找到即停（你已滚瓜烂熟的"栈 + 短路"）。

**优先级（高 → 低）**：

```
命令行参数 > 系统属性 > OS 环境变量
────────────────（config data 层）────────────────
    Nacos import 的文档  >  导入它的那份 yml
    jar 外 yml  >  jar 内 yml
────────────────────────────────────────────────
代码兜底（setDefaultProperties）
```

> 覆盖规律统一为：**逐键合并，高优先级层"赢"**；没被高优先级层写过的键，低层照常生效。

---

## 二、书写全谱：配置的 6 种来源

| # | 来源 | 书写方式 | 场景归属 |
|---|------|---------|---------|
| ① | jar 内 `application.yml` | 工程 `src/main/resources/` 里写 | 本地 + K8s（一起进镜像） |
| ② | jar 外 `application.yml` | 放在 jar 旁边 / `./config/` 目录 | 传统运维改配置方式 |
| ③ | **Nacos 配置中心** | 控制台按 Data ID 写"键补丁" | 集中治理 / 热更新 |
| ④ | **OS 环境变量 / K8s env** | `export`、Deployment `env`、`set env` | K8s 主流方式 |
| ⑤ | Java 系统属性 | `-Dkey=value`（启动参数） | 调试 / 特殊注入 |
| ⑥ | 命令行参数 | `java -jar app.jar --key=value` | 调试利器（最高优先级） |

### 2.1 ①jar 内 application.yml（最基础）

```yaml
# 本项目的写法（每键都带"占位符 + 默认值"）
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/research_agent}
```

含义：**"默认听环境变量 `DB_URL`，没有就用本地默认值"**——这是本项目"环境无关"的地基写法。

### 2.2 ③Nacos（远程 yml）书写规则

- Nacos 控制台 → 配置管理 → 配置列表 → 新建：`Data ID=research-agent-backend.yaml / Group=DEFAULT_GROUP / 格式=YAML`；
- 内容 = **"键补丁"**：写几个键覆盖几个键（逐键合并，**不是全量替换文件**）。

```yaml
# Nacos 上的 research-agent-backend.yaml 示例（只覆盖想接管的键）
spring:
  data:
    redis:
      host: redis-svc
app:
  llm:
    max-steps: 20        # 业务参数——热更新首选场景
```

### 2.3 ④K8s env 的两种形态

```yaml
# 形态 A：Deployment env 直写（明文适合非敏感）
env:
  - name: DB_URL
    value: "jdbc:postgresql://postgres-svc:5432/research_agent"

# 形态 B：从 Secret 引用（敏感项——密码永不裸写）
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: pg-secret
        key: POSTGRES_PASSWORD
```

> **进阶认知**：ConfigMap/Secret 除了"注成 env"，还能**挂成文件**（volume 挂到 `/config/application.yml`）——那它就变身"来源② jar 外 yml"了。同一份配置，两种"来源身份"。

### 2.4 ⑤⑥ 调试双枪

```bash
# 系统属性（JVM 参数）
java -Dapp.llm.max-steps=30 -jar app.jar

# 命令行参数（最高优先级，临时验证神器）
java -jar app.jar --app.llm.max-steps=99
```

---

## 三、读取全谱：代码侧的 4 种方式

### 3.1 方式一：Environment API（地基）

```java
@Autowired Environment env;
String v = env.getProperty("app.llm.chat-model");
```

- 所有方式的共同底层；业务代码基本不直接用（框架/调试用）。

### 3.2 方式二：`@Value`（单键注入）

```java
// 精确键名（推荐用 yml 里同样的 kebab 写法）
@Value("${app.llm.chat-model}")
private String chatModel;

// 带默认值：拉不到就用冒号后的
@Value("${app.llm.max-steps:12}")
private int maxSteps;

// SpEL 表达式（#{...}，能算数/调方法）
@Value("#{${app.llm.max-steps} * 2}")
private int doubled;
```

**短板清单**（决定"什么时候别用它"）：

| 短板 | 说明 |
|------|------|
| 一个注解一个键 | 一组配置要写 N 个注解 |
| 键名易错 | 建议照抄 yml 的 kebab 形式；写错只有启动时才爆 |
| 复杂类型弱 | List/嵌套对象/Map 很别扭 |
| 无校验 | 想约束取值范围做不到 |
| **Nacos 不自动热更** | 需所在 Bean 加 `@RefreshScope` |

### 3.3 方式三：`@ConfigurationProperties`（成组绑定，官方推荐 ★本项目风格）

**① 声明**（本项目真实文件：`LlmProperties.java`）：

```java
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String provider,
        String chatModel,
        double temperature,
        int maxSteps) {
}
```

**② 注册成 Bean**（三选一；本项目用 `@EnableConfigurationProperties`，见 `PropertiesConfiguration.java`）：

```java
@Configuration
@EnableConfigurationProperties({ ExecutionProperties.class, LlmProperties.class })
public class PropertiesConfiguration { }
```

**③ 消费**（本项目真实用法：`@Bean` 方法参数注入，见 `AsyncExecutionConfiguration.java`）：

```java
@Bean(name = "researchTaskExecutor")
public Executor researchTaskExecutor(ExecutionProperties executionProperties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(executionProperties.asyncThreadPoolSize());   // 配置 → 线程池
    ...
}
```

**能力全解**：

| 能力 | 说明 | 本项目实例 |
|------|------|-----------|
| **宽松绑定** | `chat-model` = `chatModel` = `CHAT_MODEL` 都能对上 | `app.execution.async-thread-pool-size` ↔ `asyncThreadPoolSize` |
| 类型转换 | 字符串自动转 `int/double/boolean/Duration/List/嵌套对象` | `"0.2"` → `double temperature` |
| 校验 | 类上加 `@Validated` + JSR-303（`@NotNull`、`@Min`…） | （可扩展点） |
| IDE 提示 | pom 有 `spring-boot-configuration-processor` 即自动补全 | 已具备 |
| **Nacos 热更新** | 刷新时**自动重绑定**（无需 @RefreshScope） | 未来接配置中心时白赚 |

### 3.4 方式四：其他（了解即可）

- `@PropertySource`：导入额外 `.properties`（不支持 yml，老风格）；
- `Binder` API：纯编程式绑定（框架级代码用）。

### 3.5 读取方式对照总表

| 维度 | `@Value` | `@ConfigurationProperties` |
|------|----------|---------------------------|
| 粒度 | 单个键 | **一组键**（按前缀） |
| 宽松绑定 | 建议照抄 kebab 键名（不要玩花样） | ✅ 原生全支持 |
| 复杂类型 | 弱（SpEL 硬拼） | ✅ 嵌套/List/Map/Duration |
| 校验 | ❌ | ✅ `@Validated` |
| IDE 提示 | ❌ | ✅ |
| Nacos 热更新 | 需 `@RefreshScope` | ✅ 自动重绑定 |
| 适合 | 零散单键 | **成组配置（推荐）** |

---

## 四、三场景横向对比（核心）

### 4.1 场景一：本地开发

| 环节 | 做法 |
|------|------|
| **书写** | 只动 `application.yml`：所有键用 `${VAR:默认值}`（默认值 = 本地开发直接可用的值） |
| **读取** | `@Value` / `@ConfigurationProperties` / Environment 都可用（本地最简单） |
| **流程** | IDE/命令行启动 → yml 进 classpath → 登记进 Environment → 注入 |
| **临时改值** | IDE 运行配置加环境变量、或 `mvn spring-boot:run -Dspring-boot.run.arguments="--app.llm.max-steps=30"` |

```yaml
# 本地书写样本（本项目 application.yml 现状）
spring:
  application:
    name: research-agent-backend
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/research_agent}
```

### 4.2 场景二：K8s 部署

| 环节 | 做法 |
|------|------|
| **书写（两处协作）** | ① yml（进镜像）= 默认值骨架；② **Deployment env**（含 ConfigMap/Secret）= 环境差异 |
| **读取** | 代码**零改动**——`@Value`/`@ConfigurationProperties` 照常用 |
| **流程** | `mvn package → docker build → 分发 → apply` → env 注入 → 栈覆盖 → 生效 |
| **改值流程** | 改清单/`set env` → **滚动更新（重启）**——这是"重启型"配置通道 |

```yaml
# 真实样本（本项目 research-agent Deployment 的关键 env）
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "pg"
  - name: DB_URL
    value: "jdbc:postgresql://postgres-svc:5432/research_agent"
  - name: DB_PASSWORD
    valueFrom: { secretKeyRef: { name: pg-secret, key: POSTGRES_PASSWORD } }
  - name: NACOS_ADDR
    value: "nacos-svc:8848"
```

**要点**：env 名走 **relaxed binding 推导**（`SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` ↔ `spring.cloud.nacos.discovery.server-addr`），或者像本项目一样用**自定义短变量**（`NACOS_ADDR`）让 yml 里 `${NACOS_ADDR:...}` 引用——后者更好读。

### 4.3 场景三：Nacos 配置中心

| 环节 | 做法 |
|------|------|
| **书写** | 控制台写 Data ID（**键补丁**，逐键覆盖） |
| **接入（2 处）** | pom 加 `spring-cloud-starter-alibaba-nacos-config`（micro-lab 已具备）+ yml 加 `spring.config.import: optional:nacos:dataId.yaml` |
| **读取** | `@ConfigurationProperties`：★ 自动热更新；`@Value`：需 `@RefreshScope` |
| **流程** | 启动拉取 → 合并进栈（覆盖本地同键）→ 长轮询监听 → 发布即热更（8 步时序见下） |
| **优势** | **不重启**（"热更新型"配置通道）——与 K8s env 的"重启型"互补 |

**读配置的完整时序**（micro-lab 第 5 站 + 原理深挖）：

```text
① 启动极早阶段：本地 yml 里发现 spring.config.import: nacos:xxx.yaml
② 用 config.server-addr 连 Nacos，按 Data ID 拉取
③ 拉到的 yml 作为"新文档"插入 Environment（在"导入它的文件"之后 → 同键远程赢）
④ 应用代码/自动配置向栈取值——对"值从哪层来"完全无感
⑤ 客户端挂长轮询（挂问 30s：无变更 304 / 有变更立即返回）
⑥ 控制台发布 → 变更通知 → 拉取新内容 → 发布 RefreshEvent
⑦ @RefreshScope Bean 重建 / @ConfigurationProperties 自动重绑定
⑧ 不重启，新值生效
```

**覆盖粒度**（逐键补丁）：

| Nacos 里写了 | 结果 |
|-------------|------|
| 某键 | 覆盖本地同键 |
| 没写的键 | 本地保持 |
| List 类型 | 整体替换（不逐元素合并） |

---

## 五、交叉速查

### 5.1 同一个键（DB URL）的三种写法

| 场景 | 写在哪 | 写什么 |
|------|--------|--------|
| 本地开发 | `application.yml` | `url: ${DB_URL:jdbc:postgresql://localhost:5432/research_agent}` |
| K8s 部署 | Deployment env（+Secret） | `env: [{name: DB_URL, value: "jdbc:postgresql://postgres-svc:5432/research_agent"}]` |
| Nacos | 控制台 dataId | `spring.datasource.url: jdbc:postgresql://postgres-svc:5432/research_agent` |

### 5.2 改一个值，怎么生效？（决策树）

```
你要改的配置在哪层？
├─ jar 内 application.yml ──→ 本地：直接改；K8s：重打包→重建镜像→重新部署
├─ K8s 清单 / set env ─────→ kubectl apply → 滚动更新（重启生效）
├─ Secret / ConfigMap ─────→ apply → 滚动更新
├─ Nacos dataId ───────────→ 控制台发布 → 热更新（CP 自动重绑定；@Value 需 @RefreshScope）
└─ 命令行/系统属性 ─────────→ 重启进程即生效（调试场景）
```

### 5.3 "读取方式 × 场景"矩阵

| 读取方式 | 本地 | K8s | Nacos | 备注 |
|---------|------|-----|-------|------|
| Environment API | ✓ | ✓ | ✓ | 地基 |
| `@Value` | ✓ | ✓ | ✓（热更需 `@RefreshScope`） | 单键 |
| `@ConfigurationProperties` | ✓ | ✓ | ✓（**自动重绑定**） | ★ 推荐 |
| `@PropertySource` | ✓ | △（文件进镜像才行） | — | 老风格 |

---

## 六、实战范例：一个键的三场景旅程

以 `app.llm.max-steps` 为例（本项目 `LlmProperties.maxSteps`）：

```yaml
# ① 本地：application.yml（默认值 12）
app:
  llm:
    max-steps: 12
```

```yaml
# ② K8s：Deployment env（进容器后覆盖成 16）——注意 relaxed 推导
env:
  - name: APP_LLM_MAX_STEPS      # ↔ app.llm.max-steps
    value: "16"
```

```yaml
# ③ Nacos：dataId 里写（再覆盖成 20；且支持热更）
app:
  llm:
    max-steps: 20
```

**最终生效值**（全栈同时存在时）：

```
栈（高→低）：K8s env(16) ？ Nacos(20) ？ 本地(12) ？
——OS 环境变量层 > config data 层（Nacos）> jar 内 yml
——所以：16 赢（env 最高）；把 env 删掉 → 20 赢；Nacos 也没有 → 12。
```

**读取侧代码（两种姿势对照）**：

```java
// 推荐：@ConfigurationProperties（改 Nacos 不重启、自动重绑定）
LlmProperties.maxSteps()

// 备选：@Value（想热更必须挂 @RefreshScope）
@RefreshScope
@Component
public class X { @Value("${app.llm.max-steps:12}") int maxSteps; }
```

---

## 七、常见坑清单

| # | 坑 | 症状 | 解法 |
|---|----|------|------|
| 1 | `@ConfigurationProperties` 没注册 | 绑定不生效、值为 null | @Component / @EnableConfigurationProperties / @ConfigurationPropertiesScan 三选一 |
| 2 | `@Value` 改名一写错 | 启动爆炸（找不到占位符） | 用 kebab 键名照抄 yml；兜底 `:默认值` |
| 3 | Nacos 改了没反应 | 通"值没变" | 查是否是 @Value（要 @RefreshScope）；@ConfigurationProperties 自动重绑定 |
| 4 | Nacos 配置没拉到 | 启动失败 / 静默用本地 | `optional:` 前缀 = 宽容；去掉 = fail-fast（生产按需选） |
| 5 | 复杂类型想热更 | 连接池等"已建对象"不重建 | 热更只更新"配置对象"——连接型配置请走"重启型"通道 |
| 6 | Nacos 与本地"以为全量替换" | 本地某些键"意外还在" | 记住是**逐键补丁**；想清空某键显式覆盖 |
| 7 | List 配置合并 | 元素"丢了" | List 是整体替换 |
| 8 | 敏感项进了 Nacos 明文 | 安全风险 | 密码走 Secret / Nacos 加密配置 |

---

## 八、企业实践速览：什么配置该放哪

| 配置类型 | 例子 | 推荐去处 |
|---------|------|---------|
| 环境相关（连接地址） | DB URL、Redis host/port | **K8s env**（重启型）或 **Nacos + namespace 分环境**（统一治理） |
| 运行期可调（业务） | 限流阈值、开关、超时、文案 | **Nacos**（热更新是杀手锏） |
| 敏感项 | 密码、API Key | **Secret** / Nacos 加密——永不裸明文 |
| 永不变化 | 端口、应用名 | 本地 yml 写死即可（无环境差异） |

> **一句话**：**"重启型"通道（K8s env/Secret）管"环境连接与敏感"；"热更型"通道（Nacos）管"运行期可调"；本地 yml 永远只是"默认值骨架"**——三通道各司其职，由"栈规则"统一合成。
