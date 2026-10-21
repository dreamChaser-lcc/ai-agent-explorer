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

1. 同一个 key 在 主 yml / dev yml / 环境变量 都写了，谁生效？
   **参考答案**：**环境变量**（优先级 3）> dev yml（4）> 主 yml（5）。实测三幕已证明（`from-env` 赢 `from-dev-yml`）。
2. 为什么环境变量名要写成 `SP6_DEMO_NAME` 这种全大写下划线？
   **参考答案**：操作系统环境变量只能用大写字母+下划线（惯例 + 兼容性）；Spring 的松散绑定会自动把 `sp6.demo.name` 映射到 `SP6_DEMO_NAME`（点/短横线 → 下划线，转大写）。
3. `@Value` 和 `@ConfigurationProperties` 怎么选？
   **参考答案**：**成组配置**（一个前缀下一组相关项）用 `@ConfigurationProperties`——支持松散绑定、集合/Map/嵌套对象、校验、构造器绑定；**单个值**（如一个开关）用 `@Value` 更轻。
4. 生产环境要改一个配置，不想重新打包，有几种办法？
   **参考答案**：① 环境变量（容器/服务器设置）；② 命令行参数（启动脚本加 `--key=value`）；③ 配置中心（Nacos/Config Server，改完刷新）。三者优先级都高于 jar 内 yml——**"配置外置"正是靠优先级设计实现的**。

---

## 八、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| `backend/.../infrastructure/config/Sp6DemoProperties.java` | `sp6.demo.*` 配置属性（name/source/tags） | ✅ 可删 |
| `backend/.../infrastructure/config/Sp6DemoConfiguration.java` | 启用配置绑定 | ✅ 可删 |
| `backend/.../infrastructure/config/Sp6DemoRunner.java` | 启动后打印最终取值 | ✅ 可删 |
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

（待补——学员提问后按双台账纪律记录）
