# T3 · API 文档自动生成（SpringDoc / Knife4j）

> **课程**：技术栈补齐实战 · 一期（2026-10-25）
> **规范依据**：[live-java 规范](../技术栈补齐实战/技术栈对照-live-java规范.md) 4「编码规范」：接口加 Swagger 文档
> **目标**：理解"文档从代码提取"的完整链路，并把文档接进项目（实测通过）
> **状态**：✅ 完成（17 个接口自动生成；踩通两个版本坑）

---

## 一、为什么需要（手写文档的四个问题）

| 问题 | 后果 |
|---|---|
| 手写容易过期 | 代码改了文档没改 → 前端按旧文档对接 → 联调翻车 |
| 无法校验 | 文档和实际接口不符，没人发现 |
| 无法生成 SDK | 前端要手写类型定义 |
| 无法自动测试 | 无 spec 就无法生成 Mock/契约测试 |

**核心认知**：**文档应该是"从代码提取的副产品"，不是"另外写一份"**。

---

## 二、技术原理（三步，所有方案都一样）

```text
① 代码里写注解（可选，不加也能生成基础文档）
     @Tag(name = "任务管理")
     @Operation(summary = "创建研究任务", description = "...")

② 扫描 → 生成 OpenAPI 3 JSON（标准格式）
     GET /v3/api-docs →  {"openapi":"3.0.1","paths":{...}}

③ UI 渲染
     Swagger UI / Knife4j / ReDoc → 把 JSON 变成可交互页面
```

**三个角色的分工**：

| 角色 | 职责 |
|---|---|
| 注解 | 提供元数据（可选增强） |
| springdoc | 扫描 Spring MVC 的 HandlerMapping（**和方法反射**），生成 spec |
| UI | 纯前端，读取 spec 渲染页面 |

---

## 三、五种方案对比

| 方案 | 原理 | 状态 | 适用 |
|---|---|---|---|
| Swagger 2（springfox） | 注解扫描 | ❌ 已停止维护 | 老项目（Boot 2） |
| **SpringDoc OpenAPI 3** | 注解扫描 | ✅ **事实标准** | **Boot 3 项目（本项目选用）** |
| Knife4j | 基于 SpringDoc 的国产增强 UI | ⚠️ 版本滞后 | 国内常用（但 Boot 3.5 有坑，见 §四） |
| Spring REST Docs | **测试驱动**（跑测试生成片段） | ✅ 严谨团队 | 支付/银行（准确性第一） |
| Apifox / YApi | 平台化管理 spec | ✅ 企业协作 | 前后端联调 |

---

## 四、实测：给项目接入（含两个版本坑）

### 4.1 第一次尝试：Knife4j 4.5.0（失败）

```xml
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>4.5.0</version>
</dependency>
```

**结果**：`/doc.html` 返回 200，但 `/v3/api-docs` 只有 145 字符（空 spec，0 接口）。

**日志**：

```text
jakarta.servlet.ServletException: Handler dispatch failed:
java.lang.NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
```

**根因**：Knife4j 4.5.0 内置 **springdoc 2.3.0**，它依赖 **Spring Framework 6.0** 的 `ControllerAdviceBean(Object)` 构造器；**Boot 3.5（Spring 6.2）已删除该构造器**。

### 4.2 第二次尝试：排除 + 覆盖版本（仍失败）

```xml
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>4.5.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

**结果**：换成另一个错——

```text
NoSuchMethodError: 'java.util.List org.springdoc.core.properties.SpringDocConfigProperties.getGroupConfigs()'
at ... springdoc-openapi-starter-common-2.8.6.jar
```

**根因**：**只排除了 `webmvc-ui`，但 Knife4j 还传递依赖 `springdoc-openapi-starter-common` / `starter-webmvc-api`（2.3.0）**——两个版本混在 classpath，类加载顺序随机 → 方法对不上。

### 4.3 第三次：放弃 Knife4j，只用 SpringDoc（成功）

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

**结果**：

```text
① /v3/api-docs      → 19232 字符，17 个接口（16 个路径）
② /swagger-ui.html  → HTTP 200
③ 异常数            → 0
```

**结论**：**Boot 3.5 上直接用 springdoc 2.8.x 最稳**（UI 功能等价，只是皮肤朴素）。

### 4.4 补充：文档元信息与注解

**元信息**（不加则显示 "OpenAPI definition / v0"）：

```java
@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI researchAgentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Research Agent API")
                .version("1.0.0")
                .description("研究任务编排系统接口文档（SpringDoc 2.8.6 自动生成）"));
    }
}
```

**注解**（`@Tag` 分组 + `@Operation` 说明）：

```java
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "研究任务的创建、查询、控制")
public class ResearchTaskController {

    @Operation(summary = "创建研究任务", description = "首次创建 201；幂等重放 200")
    @PostMapping
    public ResponseEntity<TaskSummaryResponse> createTask(...) { }

    @Operation(summary = "分页查询任务列表", description = "size 服务端上限 100 条")
    @GetMapping
    public PageResponse<TaskSummaryResponse> listTasks(...) { }
}
```

**实测生效**：

```text
元信息：Research Agent API / 1.0.0
summary：分页查询任务列表 | 创建研究任务
tags：任务管理
接口数：17
```
---

## 五、企业完整流程（6 步）

```text
① 开发阶段：写接口 + 加注解
      @Tag(name = "任务管理")
      @Operation(summary = "创建任务", description = "同步/异步由 executionMode 决定")
      @Schema(description = "任务标题", example = "调研 Java Agent")

② 本地阶段：跑起来看效果
      http://localhost:8080/swagger-ui.html        （SpringDoc 官方 UI）
      http://localhost:8080/doc.html               （Knife4j，注意版本兼容）

③ CI 阶段：抽取 + 校验
      构建时抓 /v3/api-docs → openapi-diff 比对
      · 检测"删字段 / 改类型 / 改必填"（breaking change）
      · 发现则 CI 失败 → 拦住合并（保护前端）

④ 发布阶段：推到文档平台
      方式 A：静态托管（doc.html + JSON 发 Nginx / GitHub Pages）
      方式 B：上传 Apifox / YApi（前后端同平台）
      方式 C：网关聚合（微服务场景，一个 UI 看所有服务）

⑤ 版本管理：spec 跟代码版本走（v1 / v2 并存）

⑥ 前端对接：自动生成 SDK / Mock
      openapi-typescript api.json -o types.ts     → TS 类型
      prism mock api.json                         → 假接口
      → 后端没写完，前端已能联调
```

---

## 六、企业级三件"加码"

### ① 网关聚合（微服务场景）

```yaml
springdoc:
  swagger-ui:
    urls:
      - name: 任务服务
        url: /task/v3/api-docs
      - name: 报告服务
        url: /report/v3/api-docs
```

**一个 UI 看全部服务**——micro-lab 那类多模块项目必须这么做。

### ② CI 拦住 breaking change

```text
openapi-diff old.json new.json
  → 检测到"删除字段 / 改类型 / 改必填"
  → 非 0 退出码 → CI 失败 → 禁止合并
```

**这才是企业文档体系的核心价值**：不是"给人看"，而是**契约保护**。

### ③ 生成 SDK / Mock

```text
openapi-typescript  → 前端类型定义（API 改动编译期就能发现）
prism mock          → 前端可用的假接口
```

---

## 七、SpringDoc vs Spring REST Docs

| | **SpringDoc** | **Spring REST Docs** |
|---|---|---|
| 生成方式 | 运行时**扫描注解** | **跑测试**时生成片段 |
| 准确性 | ⚠️ 注解与代码可能不同步 | ✅ 测试通过才生成（保证真实） |
| 成本 | 低（加注解） | 高（每个接口写测试） |
| 适用 | 大多数项目 | 支付/银行（准确性第一） |

**企业常见组合**：SpringDoc 做开发期文档（快）+ REST Docs 做发布期契约（准）。

---

## 八、一个规范与 Boot 3 的版本差异（重要）

live-java 规范写的是：

```java
@Api(tags = "admin-模块名称")        // Swagger 2（io.swagger.annotations）
@ApiOperation("创建任务")
```

**这是 Swagger 2 的注解**（springfox 时代）。**Boot 3 + OpenAPI 3 必须改成**：

```java
@Tag(name = "任务管理")               // OpenAPI 3（io.swagger.v3.oas.annotations）
@Operation(summary = "创建任务")
```

**导入包不同**：

| | Swagger 2 | OpenAPI 3 |
|---|---|---|
| 包 | `io.swagger.annotations` | `io.swagger.v3.oas.annotations` |
| 分组 | `@Api(tags=...)` | `@Tag(name=...)` |
| 方法说明 | `@ApiOperation` | `@Operation` |
| 字段说明 | `@ApiModelProperty` | `@Schema` |

**规范里这套注解在本项目用不了**（缺 springfox 且不兼容 Boot 3）——又一处"版本基线不同"。

---

## 九、生产环境必须关闭

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}     # 生产 false
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:false}
```

**为什么**：文档暴露全部接口结构（路径、参数、错误码），属于内部信息；且 Swagger UI 可被用来"试打"接口。

---

## 十、思考题（自查）

1. 为什么 `/doc.html` 返回 200，但 spec 是空的？<br>
   **参考答案**：UI 是纯静态资源（能加载），但生成 spec 的后端逻辑抛了 `NoSuchMethodError`（版本不兼容）→ spec 为空。**判断文档是否真的生效，要看 `/v3/api-docs` 的长度和接口数，不是看 UI 能不能打开**。

2. 为什么排除了 `springdoc-openapi-starter-webmvc-ui` 还是失败？<br>
   **参考答案**：springdoc 是**多 artifact 拆分**的（`starter-common` / `starter-webmvc-api` / `starter-webmvc-ui`）。只排除一个，剩下的仍是旧版本 → 与新版本混搭 → 类加载顺序随机 → `NoSuchMethodError`。**要排除一个 starter 的全部传递依赖，或直接用官方 springdoc**。

3. CI 里为什么要做 `openapi-diff`？<br>
   **参考答案**：文档的价值不只是"给人看"，而是**前后端契约**。删字段/改类型/改必填会让前端编译报错或运行崩溃——CI 拦住 breaking change，等于**把契约问题左移到合并前**。

4. `@Api` 和 `@Tag` 能混用吗？<br>
   **参考答案**：不能。`@Api` 是 Swagger 2（`io.swagger.annotations`），`@Tag` 是 OpenAPI 3（`io.swagger.v3.oas.annotations`）。Boot 3 项目只能用后者；混用会导致注解被忽略或编译失败。

---

## 十一、本课文件清单

| 文件 | 变更 | 说明 |
|---|---|---|
| [`pom.xml`](../../backend/pom.xml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/pom.xml) | **加依赖** | `springdoc-openapi-starter-webmvc-ui:2.8.6`（注释里记录了 Knife4j 两次踩坑） |
| [`application.yml`](../../backend/src/main/resources/application.yml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml) | **加配置** | `springdoc.api-docs` / `swagger-ui` + `SPRINGDOC_ENABLED` 开关 |
| [`OpenApiConfiguration.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/OpenApiConfiguration.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/OpenApiConfiguration.java) | **新增** | 文档元信息（title/version/description） |
| [`ResearchTaskController.java`](../../backend/src/main/java/com/aiexplorer/researchagent/api/controller/ResearchTaskController.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/controller/ResearchTaskController.java) | **加注解** | `@Tag` + 2 个 `@Operation`（演示注解效果） |

> 全部为生产可用（文档是刚需）；`SPRINGDOC_ENABLED=false` 即可在生产关闭。

---

## 十二、本课问答记录（2026-10-25）

| # | 提问（原话） | 落档位置 |
|---|---|---|
| **Q1** | "SpringDoc 也是基于 Swagger 弄的吗，为什么路由会是 /swagger-ui.html" | §12.1「SpringDoc / OpenAPI / Swagger UI 三层关系」（见下，含 302 实测） |
| **Q2** | "@Operation 这个注解是干嘛的" | §12.2「@Operation：给接口写说明书」（见下，含有无注解的 spec 对比实测） |
| **Q3** | "这个是什么内容，schemas" | §12.3「Schemas：数据模型字典」（见下，含 10 个 schema 的项目映射） |

### 12.1 SpringDoc、OpenAPI、Swagger UI 的三角关系（Q1 · 2026-10-25）

#### 一、三层分开看

| 层 | 名字 | 是什么 | 项目实测 |
|---|---|---|---|
| **规范** | OpenAPI Specification **3.1.0** | 标准格式（JSON/YAML 描述 API） | spec 里 `"openapi": "3.1.0"` |
| **实现** | **SpringDoc 2.8.6** | 扫描 Spring MVC 生成 OpenAPI 文档 | 依赖树三件套均 2.8.6 |
| **UI** | **Swagger UI** | 渲染工具（读 spec 显示页面） | 被 SpringDoc 内置 |

**SpringDoc 不是"基于 Swagger 写的"，而是：实现 OpenAPI 规范 + 内置官方 Swagger UI。**

#### 二、名字的历史

```text
2010  Swagger 项目（SmartBear 的工具 + 规范）
2014  Swagger 2.0 规范（springfox 实现，注解 @Api/@ApiOperation）
2015  规范捐给 Linux 基金会 → 改名 OpenAPI Specification（工具品牌保留）
2017  OpenAPI 3.0
2020  OpenAPI 3.1（本项目）
```

**规范改名了，工具没改名**——所以 UI 还叫 Swagger UI。

#### 三、为什么路由是 `/swagger-ui.html`（实测）

```text
GET /swagger-ui.html      → HTTP 302 → Location: /swagger-ui/index.html
GET /swagger-ui/index.html → HTTP 200

jar 里：
  org/springdoc/webmvc/ui/SwaggerWelcomeCommon.class
  org/springdoc/webmvc/ui/SwaggerWelcomeWebMvc.class    ← ★ 做重定向的类
```

**`SwaggerWelcome` 类名即答案**：SpringDoc 专门有个类把用户"欢迎"到内置 Swagger UI。

**为什么 302**：`/swagger-ui.html` 是入口别名，`/swagger-ui/index.html` 是静态资源真实路径——解耦后改资源路径不影响对外 URL。

**可改路径**：`springdoc.swagger-ui.path=/docs`

#### 四、命名混乱对照

| 你看到的 | 真实含义 | 易误解成 |
|---|---|---|
| `/v3/api-docs` | **OpenAPI 3** 的 spec | "Swagger 3"（**Swagger 规范只到 2.0**） |
| `io.swagger.v3.oas.annotations` | 包名保留 swagger 品牌，内容是 OpenAPI 3 | 以为在用 Swagger 2 |
| `@Tag` / `@Operation` | OpenAPI 3 注解 | 与 `@Api`/`@ApiOperation` 混淆 |
| `/swagger-ui.html` | Swagger **UI**（渲染工具） | 以为是老框架路由 |

```text
Swagger 规范：只到 2.0（springfox，已停维护）
OpenAPI 规范：3.0 / 3.1（springdoc，活跃）
Swagger UI  ：渲染工具，两代都在用（官方维护）
```

#### 五、`/doc.html` 是哪来的

| UI | 路由 | 特点 |
|---|---|---|
| Swagger UI（官方） | `/swagger-ui.html` | 功能完整、界面朴素、稳定 |
| Knife4j | `/doc.html` | 中文友好、调试强，但**版本滞后**（本课踩坑） |

**两者读的是同一份 `/v3/api-docs`**，只是皮肤不同。

### 12.2 @Operation：给接口写说明书（Q2 · 2026-10-25）

#### 一、一句话

```text
@Operation = 给【一个接口方法】写说明书
            → 内容进入 /v3/api-docs 的 spec → 显示在 Swagger UI 上
```

#### 二、实测对比（2026-10-25）

```text
有 @Operation（GET /api/tasks）：
  summary     = 分页查询任务列表
  description = 按创建时间倒序；size 服务端上限 100 条

没 @Operation（GET /api/tasks/{taskId}）：
  summary     = （空）
  description = Bad Request      ← 400 响应的默认文案，不是接口说明（误导）
  operationId = getTaskDetail    ← SpringDoc 从方法名自动推断
```

**不写的后果**：UI 里只剩 `getTaskDetail` 这种方法名，前端看不出接口干什么。

#### 三、常用属性

| 属性 | 作用 | 示例 |
|---|---|---|
| **`summary`** | 一句话标题（列表显示，最重要） | `"创建研究任务"` |
| **`description`** | 详细说明（展开显示） | `"首次 201；幂等重放 200"` |
| `tags` | 分组（覆盖类上的 @Tag） | `{"任务管理"}` |
| `operationId` | 唯一标识（生成 SDK 的方法名） | `"createTask"` |
| `deprecated` | 标记废弃（UI 删除线） | `true` |
| `parameters` / `responses` | 参数/响应说明（配合下面的注解） | — |

#### 四、配套五个注解

```java
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "研究任务的创建、查询、控制")   // ① 类级分组
public class ResearchTaskController {

    @Operation(summary = "创建研究任务", description = "首次 201；幂等重放 200")   // ② 方法级说明
    @ApiResponses({                                                              // ③ 响应码
            @ApiResponse(responseCode = "201", description = "创建成功"),
            @ApiResponse(responseCode = "200", description = "幂等重放"),
            @ApiResponse(responseCode = "400", description = "参数校验失败")
    })
    @PostMapping
    public ResponseEntity<TaskSummaryResponse> createTask(
            @Parameter(description = "任务标题，不能为空")                       // ④ 参数说明
            @Valid @RequestBody CreateResearchTaskRequest request) { ... }
}
```

| 注解 | 位置 | 作用 |
|---|---|---|
| `@Tag` | 类上 | 接口**分组**（UI 左侧菜单） |
| **`@Operation`** | 方法上 | 接口**说明**（summary + description） |
| `@ApiResponse(s)` | 方法上 | **响应码**说明 |
| `@Parameter` | 参数上 | **参数**说明（含义/示例） |
| `@Schema` | DTO 字段上 | **字段**说明 |
| `@Hidden` | 类/方法上 | **隐藏**接口 |

#### 五、与 @ApiOperation 的区别

| | `@ApiOperation`（Swagger 2） | **`@Operation`（OpenAPI 3）** |
|---|---|---|
| 包 | `io.swagger.annotations` | `io.swagger.v3.oas.annotations` |
| 状态 | ❌ 随 springfox 停维护 | ✅ 当前标准 |
| 写法 | `@ApiOperation("创建任务")` | `@Operation(summary = "创建任务")` |
| 响应 | `@ApiResponse(code=200, ...)` | `@ApiResponse(responseCode="200", ...)` |

> live-java 规范里的 `@ApiOperation` 是 Swagger 2 的，Boot 3 项目必须换 `@Operation`。

#### 六、不写能行吗

```text
能生成文档（从方法名/参数类型推断），但质量差：
  · 没有中文说明（只剩 getTaskDetail）
  · description 显示默认错误文案（误导）
  · 前端要读代码才知道接口干什么
```

| 做法 | 成本 | 效果 |
|---|---|---|
| 不写 | 0 | 能看但看不懂 |
| `@Tag` + `@Operation(summary)` | 每接口 1 行 | **80% 价值** |
| 全套（+ @ApiResponse + @Schema） | 每接口 3-5 行 | 100%（含错误码与字段说明） |

#### 七、项目当前状态

```text
✅ 已加：ResearchTaskController 的 @Tag + 2 个 @Operation
❌ 未加：其余 8 个 Controller（约 15 个接口）
❌ 未加：DTO 的 @Schema（字段说明）
```

补齐约 30 分钟（每个 Controller 一行 @Tag、每个方法一行 @Operation）。

---

## 十三、启动失败排查：H2 文件锁（2026-10-25 实战）

### 现象

运行 `mvn spring-boot:run` 后 `BUILD FAILURE`，日志尾部是：

```text
java.lang.InterruptedException
  at ArrayBlockingQueue.take(ArrayBlockingQueue.java:420)
  at DefaultPublisher.openEventHandler(DefaultPublisher.java:111)   ← Nacos 后台线程
```

### 判断：这些是关闭噪音，不是原因

**规律**：日志**尾部**的 `InterruptedException` / 线程中断，基本是"上下文关闭时后台线程被打断"的噪音。真正死因在**更早的位置**。

### 真正的根因（往上翻）

```text
ERROR o.h.engine.jdbc.spi.SqlExceptionHelper :
  Database may be already in use: "backend/data/h2db/research-agent.mv.db"

Caused by: org.h2.mvstore.MVStoreException:
  The file is locked: .../research-agent.mv.db [2.3.232/7]

→ Failed to initialize JPA EntityManagerFactory
→ cancelling refresh attempt
→ 应用退出（exit code 1）
```

**根因**：**H2 「文件模式」数据库同一时刻只能被一个 JVM 打开**。已有实例持锁 → 第二个进程打不开。

### 与端口冲突的区别

| 报错关键字 | 原因 |
|---|---|
| `Port 8080 was already in use` | 端口冲突 |
| **`The file is locked: ...mv.db`** | **H2 文件锁**（本节场景） |

两者常同时存在（同机跑两个实例），但 H2 错误**先**触发。

### 四种解法

| # | 方案 | 说明 |
|---|---|---|
| ① **杀残留进程**（推荐） | `Get-Process java -ErrorAction SilentlyContinue \| Stop-Process -Force` |
| ② 换端口 `--server.port=8081` | ⚠️ **不够**——H2 文件仍被锁，照样失败 |
| ③ 改内存模式 | `jdbc:h2:mem:research-agent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`（数据不落盘） |
| ④ `AUTO_SERVER=TRUE` | 允许多进程共享（有性能限制） |

### 正确的启动命令（含前置检查）

```powershell
cd d:\project\lcc-github\ai-agent-explorer\backend

Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

$env:APP_LLM_SIMULATED="true"
$env:APP_LLM_SIMULATE_MODE="NORMAL"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" -Dmaven.test.skip=true
```

访问：`http://localhost:8080/swagger-ui.html`

### 读启动日志的正确顺序

```text
① 找第一个 ERROR（日志中上部）    ← 真正死因
② 跟 Caused by 链（最底层是根因）
③ 尾部的 InterruptedException     ← 忽略（关闭噪音）
④ Maven 的 BUILD FAILURE          ← 只是结论
```

**核心认知：Java 应用的"死因"永远在第一个 ERROR，而不是最后的异常堆栈。**

### 12.3 Schemas：数据模型字典（Q3 · 2026-10-25）

#### 一、是什么

```text
Schemas = OpenAPI spec 的 components.schemas
        = 所有【请求体 / 响应体】的数据结构定义（JSON Schema）
来源：SpringDoc 扫描 Controller 方法的参数类型与返回类型自动推导
位置：Swagger UI 页面最下方
```

#### 二、本项目 10 个 Schema 的映射

| Schema | 项目里的类 | 用途 |
|---|---|---|
| `ApiError` | `api/response/ApiError.java` | 统一错误体（`code`/`message`/`path`/`fields`） |
| `FieldViolation` | `ApiError` 内部 record | 字段级错误（`field` + `reason`） |
| `CreateResearchTaskRequest` | `api/request/...` | POST 创建任务请求体 |
| `TaskSummaryResponse` | `api/response/...` | 列表项响应 |
| `PlanConfirmationRequest` | `api/request/...` | 确认计划请求体 |
| `CleanupResult` | `infrastructure/cleanup/CleanupResult.java` | 清理结果统计 |
| `PageResponseTaskSummaryResponse` | `PageResponse<TaskSummaryResponse>` | 分页壳（泛型） |
| `TaskDetailResponse` | `api/response/...` | 详情响应 |
| `SseEmitter` | **Spring 框架类型** | ⚠️ 噪音（SSE 流无固定结构） |
| `JsonNode` | **Jackson 类型** | ⚠️ 噪音（任意 JSON） |

#### 三、三个注意点

**① `object` 是 JSON Schema 类型标记**：`object`（对象）/ `array` / `string` / `integer` / `boolean`。点 "Expand all" 看字段明细。

**② 泛型被拼进类名**：

```text
PageResponse<TaskSummaryResponse>  →  PageResponseTaskSummaryResponse
```

可读性差，用 `@Schema(name = "TaskPage")` 改名。

**③ SseEmitter / JsonNode 是噪音**——框架类型被自动推导进 schema。

去掉办法：

```java
@Operation(summary = "订阅任务进度（SSE 流）", hidden = true)   // 整个接口不进文档
@Schema(hidden = true)                                        // 只隐藏该类型
```

#### 四、Schemas 的实际价值

| 用途 | 说明 |
|---|---|
| 前端生成 TS 类型 | `openapi-typescript api.json -o types.ts` → 字段自动对齐 |
| Mock 服务 | Prism 按 schema 造假数据，前端不等后端 |
| 契约校验 | CI 比对 schema，删字段/改类型直接拦下 |
| 看字段约束 | 必填项、类型、长度一目了然 |

#### 五、本项目欠缺：DTO 缺 @Schema 说明

```text
Schema 里只有【字段名 + 类型】，没有【中文说明】
原因：DTO 没写 @Schema(description = "...")
```

补法（约 20 分钟）：

```java
public record CreateResearchTaskRequest(
        @Schema(description = "任务标题", example = "调研 Java Agent", requiredMode = REQUIRED)
        @NotBlank String title,

        @Schema(description = "研究目标（自然语言）", example = "梳理可落地的 DDD 切入点")
        @NotBlank String goal
) {}
```

补完后 Expand all 里每个字段都有中文说明——**这才是"可交付的文档"**。

### 12.4 补齐 @Schema：6 个 DTO（2026-10-25 实施记录）

#### 一、补齐范围

| DTO | 字段数 | 说明 |
|---|---|---|
| `CreateResearchTaskRequest` | 4 | 请求体 |
| `PlanConfirmationRequest` | 2 | 请求体 |
| `TaskSummaryResponse` | 9 | 列表响应 |
| `TaskDetailResponse` | 12 | 详情响应 |
| `ApiError`（含嵌套 `FieldViolation`） | 6 + 2 | 错误体 |
| `PageResponse` | 5 | 分页壳 |

**@Schema 使用数：0 → 44 处**（含嵌套）。

#### 二、两个实用写法

**① `requiredMode` 显式标注必填**（比靠 `@NotBlank` 推断更明确）：

```java
@Schema(description = "任务标题", example = "调研 Java Agent 架构",
        requiredMode = Schema.RequiredMode.REQUIRED)
@NotBlank(message = "title 不能为空") String title,
```

**② 泛型类用 `@Schema(name = ...)` 改名**：

```java
@Schema(name = "PageResponse", description = "通用分页响应壳")
public record PageResponse<T>(...)
```

**改名前**：spec 里叫 `PageResponseTaskSummaryResponse`（泛型被拼进类名，可读性差）
**改名后**：`PageResponse` ✅（实测确认）

#### 三、实测效果

```text
改前：Schema 里只有字段名 + 类型（title: string）
改后：title → "任务标题"，example = "调研 Java Agent 架构"，required = true

spec 内 description 总数 = 153
Page 相关 Schema 名 = PageResponse（改名成功）
接口数 = 17（未受影响）
```

Swagger UI 的 Expand all 现在显示：

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `title` *必填* | string | 任务标题 | 调研 Java Agent 架构 |
| `goal` *必填* | string | 研究目标（自然语言描述） | 梳理可落地的 DDD 切入点 |
| `executionMode` *必填* | enum | 执行模式：SYNC 同步 / ASYNC Kafka | ASYNC |
| `idempotencyKey` | string | 幂等键：相同 key 只创建一次 | order-20261025-001 |

#### 四、一个坑：`ClassFormatError: Extra bytes at the end of class file`

**现象**：`mvn clean compile` 返回 0，但启动报：

```text
Caused by: java.lang.ClassFormatError: Extra bytes at the end of class file
  com/aiexplorer/researchagent/infrastructure/web/RequestTraceInterceptor
```

**原因**：编译产物损坏（上次 clean 时应用仍在跑，class 文件写入不完整）。

**解法**：`mvn clean` 彻底删 target 后重新 `compile`（不是增量编译）。

**判断**：`COMPILE=0` 只代表"编译没报错"，不代表"class 文件完整"——启动时才暴露。

---

## 十四、"Unable to determine Dialect" 的误导性（2026-10-25 实战）

### 现象

```text
BeanCreationException: Error creating bean 'entityManagerFactory'
  → ServiceException: Unable to create requested service [JdbcEnvironment]
    → HibernateException: Unable to determine Dialect without JDBC metadata
       (please set 'jakarta.persistence.jdbc.url' or 'hibernate.dialect')
```

### 错误信息在误导

Hibernate 确定方言的真实顺序：

```text
① 建立 JDBC 连接
② 读数据库元数据（版本/产品名）
③ 推断方言（H2Dialect / PostgreSQLDialect）

① 失败 → ② 拿不到 → 就说"推断不出方言"
但它只报了第 ③ 步的现象，没说第 ① 步已经失败
```

**类比**：体温计显示不出度数 ≠ 没发烧，可能是体温计坏了。而错误信息却建议"换个刻度"（配 dialect），方向错了。

### 正确排查姿势

```text
❌ 别急着配 hibernate.dialect（配了照样失败，连接还是建不起来）

✅ 往上找【最初的 Caused by】，看连接为什么失败：
   file is locked              → H2 文件被其他 JVM 占用
   Connection refused          → 数据库没起 / 端口不对
   Access denied for user      → 账号密码错
   Unknown database            → 库不存在
   Communications link failure → 网络不通
```

**日志里搜这些关键字**：`file is locked` / `HikariPool` / `Connection refused` / `Database may be already in use`。

### 本次真实病因

```text
另一个 JVM（PID 28444）持有 H2 文件锁：
  data/h2db/research-agent.mv.db  ← 文件正由另一进程使用
→ 连接建不起来 → 推不出方言
```

### 解法

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
# 再启动
```

### 想彻底避开文件锁：改内存库

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" `
  "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:research-agent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" `
  -Dmaven.test.skip=true
```

| | 文件库（默认） | 内存库 |
|---|---|---|
| 数据持久 | ✅ 重启还在 | ❌ 重启清空 |
| 多实例 | ❌ 文件锁冲突 | ✅ 互不影响 |
| 适合 | 看历史数据 | 纯看文档/跑接口 |

### 一句话

```text
"Unable to determine Dialect" 是【症状】，不是【病因】
  病因永远在更深的 Caused by 里（连接为什么失败）
顺序：连接 → 元数据 → 方言；连不上就永远推不出方言
```
