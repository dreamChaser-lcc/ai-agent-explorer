# SP8 · Filter 与 Interceptor（深入）

> **课程**：Spring 基础实战 · 模块二（2026-10-21）
> **已会**：SP3 实测的执行顺序（Filter 进入 → preHandle → Controller → postHandle → afterCompletion → Filter 离开）
> **本次**：Filter 链原理与排序、**请求体只能读一次**（`HttpServletRequestWrapper` 包装实战）、**鉴权 Filter**（401 正确姿势）、**CORS 预检为什么不走 Interceptor**
> **对标**：廖雪峰 23.9「添加 Filter」
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **SP3 实测顺序**（[SP3·MVC请求处理全流程](SP3·MVC请求处理全流程.md) §六）：

```text
[Filter] 进入 → [Interceptor] preHandle → Controller
→ [Interceptor] postHandle → [Interceptor] afterCompletion → [Filter] 离开
```

- **SP3 的三层对比**：Filter 拿原始报文、Interceptor 知道目标方法、AOP 管方法调用；
- **项目现有 Filter**：`WebCorsConfiguration`（CORS）、SP3 加的 `RequestTraceFilter`。

---

## 一、Filter 链原理

### 1.1 链是怎么串起来的

```java
public class MyFilter implements Filter {
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        // 前逻辑
        chain.doFilter(request, response);   // ★ 调用"下一环"——递归下去，最后到达 DispatcherServlet
        // 后逻辑（回来时执行）
    }
}
```

**"链"不是数组遍历，而是递归**：每个 Filter 调 `chain.doFilter` 把控制权交给下一个，最后一环是 DispatcherServlet；返回时按调用栈**逆序**回来——这就是"最先进、最后出"的机制来源。

### 1.2 排序

| 方式 | 写法 | 说明 |
|---|---|---|
| `@Order(n)` | 越小越靠外 | 简单 |
| `FilterRegistrationBean` | `new FilterRegistrationBean<>(filter)` + `setOrder(n)` + `addUrlPatterns()` | 能**按路径注册**（更精确） |

**默认顺序**：没有 `@Order` 时按 bean 名/扫描顺序，**不确定**——生产里依赖顺序的 Filter 一定显式 `@Order`。

### 1.3 `OncePerRequestFilter`：为什么"一次请求只执行一次"

普通 `Filter` 在**请求转发（forward）/ 异步派发**时可能被再次调用。`OncePerRequestFilter` 用一个 request 属性打标记：

```java
// 内部逻辑（简化）：
if (request.getAttribute(ALREADY_FILTERED_ATTRIBUTE) != null) {
    chain.doFilter(request, response);   // 已执行过，直接放行
    return;
}
request.setAttribute(ALREADY_FILTERED_ATTRIBUTE, Boolean.TRUE);
doFilterInternal(request, response, chain);   // 你实现的方法
```

**实践建议**：写 Filter 一律继承 `OncePerRequestFilter`。

---

## 二、★ 请求体只能读一次：`HttpServletRequestWrapper`

### 2.1 问题

Servlet 的 `request.getInputStream()` 是**消耗型流**：

```text
Filter 里读一次 body → 流空了 → Controller 的 @RequestBody 读到空 → 反序列化失败
```

所以"记录请求体日志 / 脱敏"**必须通过包装**，不能直接读。

### 2.2 解法：包装 request

```java
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();   // ① 一次性读完，存进内存
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {                            // ② 每次都返回"新的流"
            @Override public int read() { return buffer.read(); }
            @Override public boolean isFinished() { return buffer.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    public String getBodyText() {                                    // ③ 给日志用
        return new String(cachedBody, StandardCharsets.UTF_8);
    }
}
```

**三个要点**：

1. 构造时**读一次**存字节；
2. `getInputStream()` 每次返回**新的** `ByteArrayInputStream`——Controller 读到的就是这份缓存；
3. 日志用 `getBodyText()`，**截断**（大 body 打全量会拖垮日志）。

### 2.3 代价

- body 全量进内存——**大文件上传不能这样包装**；
- 只适合 JSON API（本项目场景）。

---

## 三、鉴权 Filter 实战：401 的正确姿势

### 3.1 为什么鉴权放 Filter

| | Filter | Interceptor |
|---|---|---|
| 执行时机 | DispatcherServlet **之前** | 路由之后 |
| 拦截成本 | 最低（不进入 MVC） | 稍高（已完成路由/参数解析） |
| 能拿到目标方法 | ❌ | ✅ |

**结论**：**粗粒度鉴权（有没有 token）放 Filter**；**细粒度权限（这个方法要什么角色）放 Interceptor**（需要注解信息）。

### 3.2 401 的正确姿势

```java
if (token 无效) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":401,\"message\":\"未登录或令牌无效\"}");
    return;   // ★ 不调用 chain.doFilter ——请求到此为止
}
```

**三个坑**：

1. **必须 `return`**：继续 `chain.doFilter` 会放行到 Controller；
2. **响应体要符合统一格式**（场景① 的 `ApiResponse`）——前端只写一套解析；
3. **白名单**：`/actuator/**`、`/h2-console/**`、登录/公开接口要放行，否则探针/控制台全挂。

---

## 四、CORS 预检：为什么 Interceptor 收不到 OPTIONS

```text
浏览器     OPTIONS /api/tasks（预检，带 Access-Control-Request-Method）
   ↓
Filter 层  CORS 处理（CorsFilter / WebMvcConfigurer 的 CORS 配置由框架内置 Filter 执行）
   ↓  ★ 预检请求在这里就被应答了，不进入 DispatcherServlet
DispatcherServlet    （不会到达）
Interceptor          （不会执行）
```

**这解释了两个常见困惑**：

1. 为什么在 Interceptor 里写的"跨域逻辑"不生效——**预检根本走不到 Interceptor**；
2. 为什么 CORS 必须配在 Filter/容器层（`WebCorsConfiguration` 是正确的做法）。

**本项目**：`WebCorsConfiguration` 用 `WebMvcConfigurer.addCorsMappings`——Spring MVC 会自动注册一个 `CorsFilter` 到 Filter 链，所以预检能正常应答。
---

## 四·补、Interceptor 深入（2026-10-21 学员指出"只讲了 Filter"后补）

### 4b.1 三个方法详解

| 方法 | 时机 | 参数 | 返回值/能力 |
|---|---|---|---|
| `preHandle(req, res, handler)` | Controller **之前** | `handler` 实际是 `HandlerMethod` | **返回 false → 终止请求**；能读方法注解/参数定义 |
| `postHandle(req, res, handler, mv)` | Controller **正常返回后** | `ModelAndView`（REST 用不到） | 改 Model；**异常时不执行** |
| `afterCompletion(req, res, handler, ex)` | 请求**完成时** | `ex` = 异常（无则 null） | 清理/计时/收尾；**无论成败都执行** |

### 4b.2 ★ Interceptor 的独有能力：`HandlerMethod`

```java
if (handler instanceof HandlerMethod handlerMethod) {
    handlerMethod.getMethod();                          // 目标方法（Method 对象）
    handlerMethod.getBeanType();                        // Controller 类
    handlerMethod.getMethodAnnotation(RequireRole.class); // ★ 方法上的注解
    handlerMethod.getMethodParameters();                // 参数定义（含注解）
}
```

**Filter 只有 `request.getRequestURI()`**——它看不到"最终落到哪个方法、方法上标了什么注解"。这就是**细粒度权限必须放 Interceptor** 的根本原因。

**本课实测证据**（`PermissionInterceptor` + `@RequireRole("ADMIN")`）：

```text
[SP8-Permission] Sp8DemoController.adminOnly 需要角色=ADMIN，请求头 X-Role=缺失
```

日志里的 `Sp8DemoController.adminOnly`（方法名）与 `ADMIN`（注解值）都来自 `HandlerMethod`——**Filter 做不到**。

### 4b.3 拒绝响应的两种方式（重要区别）

| 方式 | 请求走向 | 能否复用全局异常处理 |
|---|---|---|
| `return false` + 手动写响应 | 终止，Controller 不执行 | ❌ 需手拼 JSON（格式易不一致） |
| `throw new ResponseStatusException(...)` / 自定义异常 | 冒泡到 HandlerExceptionResolver → `@RestControllerAdvice` | ✅ 自动走统一错误体（场景①） |

**推荐**：项目有统一异常处理，权限异常应 `throw` 让全局处理器兜底；本课用 `return false` 是为了让"拦截"这个动作最直观。

### 4b.4 异步请求与 SSE（项目真实场景）

`HandlerInterceptor` 只覆盖**同步**流程。异步请求（`Callable` / `DeferredResult` / **SSE**）由 `AsyncHandlerInterceptor` 扩展：

```java
public class MyAsyncInterceptor implements AsyncHandlerInterceptor {
    @Override
    public void afterConcurrentHandlingStarted(req, res, handler) {
        // 异步请求开始时调用：Controller 已交出控制权、请求线程即将释放
    }
}
```

**项目关联**：`/api/tasks/{taskId}/events` 是 SSE（异步）——请求线程会提前释放，`postHandle` / `afterCompletion` 不按同步时序执行，清理逻辑要放 `afterConcurrentHandlingStarted`。（本课未实跑，需保持 SSE 连接，标注待验证。）

### 4b.5 Filter vs Interceptor 最终对比（深化版）

| 维度 | Filter | Interceptor |
|---|---|---|
| 规范 | Servlet | Spring MVC |
| 位置 | DispatcherServlet **之前** | DispatcherServlet **之内** |
| 能拿到 | request/response（URL、头、body） | **HandlerMethod（方法、注解、参数定义）** + request/response |
| 中断请求 | 不调用 `chain.doFilter` | `preHandle` 返回 false |
| 异常处理 | 自己处理 | 抛出 → 全局异常处理器 |
| 异步支持 | 天然支持（容器层） | `AsyncHandlerInterceptor` |
| 典型用途 | 编码 / CORS / traceId / **粗粒度鉴权** | **方法级权限** / 带方法名的日志 / 参数审计 |

### 4b.6 Interceptor 三个坑

1. **`postHandle` 在 Controller 抛异常时不执行**——只有正常返回才走；清理逻辑必须放 `afterCompletion`；
2. **`preHandle` 里拿不到"参数值"**——参数解析发生在 `preHandle` **之后**，`HandlerMethod` 只有参数定义；要拿值只能在 `afterCompletion`（request attribute）或自定义 `HandlerMethodArgumentResolver`；
3. **拦截器抛异常 vs 返回 false**——语义不同：`return false` 是"正常拦截"，`throw` 是"异常"（走全局处理器，状态码与错误体由处理器决定）。

## 五、实验：三个验证（2026-10-21 全绿）

### 5.1 请求体包装（默认启动，无鉴权）

```text
[SP8-Body] 请求体（POST /api/tasks）：{"title":"SP8-请求体包装验证","goal":"...","executionMode":"SYNC"}
POST_OK status=WAITING_FOR_CONFIRMATION
```

**关键证据**：Filter 打印了完整 body 之后，日志里仍有
`binding parameter (15:VARCHAR) <- [SP8-请求体包装验证]`——**Controller 的 `@RequestBody` 正常拿到了 JSON**。

如果 Filter 里直接 `request.getInputStream().readAllBytes()` 而不包装，这条日志就会变成 400（body 已被读空）。

### 5.2 Token 鉴权（`APP_DEMO_TOKEN_AUTH_ENABLED=true` 启动）

```text
无 token：  HTTP=401  {"code":401,"message":"未登录或令牌无效（SP8 教学演示）"}
带 token：  HTTP=200
```

- 401 响应体是**统一 JSON**（不是 Tomcat 默认 HTML 错误页）；
- 白名单生效：`/actuator`、`/h2-console` 不受影响（K8s 探针不会被打挂）。

### 5.3 @Order 嵌套顺序（关键实证）

```text
无 token 的 POST：
  [SP8-Token] POST /api/tasks，X-Token=缺失        ← 只有外层，401
  （没有任何 [SP8-Body] 日志）                      ← ★ 内层 Filter 根本没执行

带 token 的 POST：
  [SP8-Token] POST /api/tasks，X-Token=已提供       ← 外层先进
  [SP8-Body] 请求体（POST /api/tasks）：{"title":"SP8-顺序-有token-正确",...}   ← 内层后进
  POST_OK status=WAITING_FOR_CONFIRMATION
```

**结论**：`@Order(1)` 的 TokenAuthFilter 在 `@Order(2)` 的 BodyCachingFilter **外层**——被外层拦截时，内层连执行机会都没有。**"先鉴权、后读体"正是靠这个顺序保证的。**

### 5.4 一个测试工具的坑（记录备查）

用 `curl.exe -d $body` 发中文 JSON 时，PowerShell 会把双引号吃掉，body 变成 `{title:...}`（非法 JSON）→ 400。
**正确做法**：用 `Invoke-RestMethod -Body ([Text.Encoding]::UTF8.GetBytes($body))`，或用 `--data-binary @文件`。

---

## 六、项目关联地图

| 概念 | 项目位置 |
|---|---|
| CORS（Filter 层） | `WebCorsConfiguration`（预检在此层应答） |
| 请求追踪 Filter | `RequestTraceFilter`（SP3） |
| 请求体包装 | `CachedBodyHttpServletRequest`（SP8） |
| 鉴权 Filter | `TokenAuthFilter`（SP8，条件启用） |
| 统一 401 响应 | 手写 JSON（生产可复用场景① 的 `ApiResponse`） |

> ⚠️ **生产提醒**：`BodyCachingFilter` 当前**无条件启用**且日志打印 body 原文——生产环境必须加开关 + 脱敏（密码/令牌字段），或只记录 body 长度与哈希。教学演示保留原样，便于观察。

---

## 七、思考题（自查 + 参考答案）

1. 为什么在 Filter 里直接读 `request.getInputStream()` 会导致 Controller 拿不到 body？
   **参考答案**：Servlet 的输入流是**消耗型**（读一次就空）。Filter 读完，Controller 的 `@RequestBody` 只能读到 0 字节 → 反序列化失败（400）。必须用 `HttpServletRequestWrapper` 在构造时缓存字节，并让 `getInputStream()` 每次返回**新的**流。
2. 401 响应为什么必须 `return`，不能继续 `chain.doFilter`？
   **参考答案**：`chain.doFilter` 会把请求交给下一环（内层 Filter / DispatcherServlet / Controller）——鉴权"拒绝了但还是执行了"，等于没鉴权。写完响应后必须 `return`。
3. 为什么 CORS 预检（OPTIONS）走不到 Interceptor？
   **参考答案**：预检请求在 **Filter 层**（Spring MVC 自动注册的 `CorsFilter`）就被应答，**不进入 DispatcherServlet**——Interceptor 位于 DispatcherServlet 之内，自然执行不到。所以 CORS 必须配在 Filter/容器层。
4. 鉴权该放 Filter 还是 Interceptor？
   **参考答案**：**粗粒度**（有没有 token、IP 是否合法）放 Filter——更早、成本更低；**细粒度**（这个方法需要什么角色，依赖方法上的注解）放 Interceptor——只有它拿得到 `HandlerMethod`。

---

## 八、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| `backend/.../infrastructure/web/CachedBodyHttpServletRequest.java` | 可重复读请求体的包装器 | ✅ 可删 |
| `backend/.../infrastructure/web/BodyCachingFilter.java` | 请求体日志 Filter（@Order(2)，**无条件启用**） | ✅ 可删 |
| `backend/.../infrastructure/web/TokenAuthFilter.java` | Token 鉴权 Filter（@Order(1)，**条件启用**，默认关闭） | ✅ 可删 |
| `backend/.../infrastructure/web/RequireRole.java` | 方法级角色注解（供 Interceptor 读取） | ✅ 可删 |
| `backend/.../infrastructure/web/PermissionInterceptor.java` | 方法级权限拦截器（HandlerMethod 读注解） | ✅ 可删 |
| `backend/.../infrastructure/web/Sp8DemoController.java` | 教学 Controller（`/api/sp8/admin-only`，标 `@RequireRole("ADMIN")`） | ✅ 可删 |
| `backend/.../infrastructure/web/Sp8InterceptorConfiguration.java` | 注册 PermissionInterceptor 到 `/api/**` | ✅ 可删 |

> 成组删除（Filter 依赖 wrapper；Interceptor 依赖注解 + 教学 Controller）；生产如要保留鉴权，需把固定 token / 固定角色换成 JWT/会话校验并补单测。

---

## 九、本课问答记录（2026-10-21）

| # | 提问（原话） | 落档位置 |
|---|---|---|
| **Q1** | "@TokenAuthFilter.java:29 这里意思也是继承了这个类，Spring Boot 会在 filter 的时候调用这个类是吗" | 本文 §一.4「为什么会自动注册（学员问答补讲）」——"被调用"的直接原因是 **Filter 类型 bean 的自动注册**，不是继承；继承 `OncePerRequestFilter` 只是获得 Filter 身份 + 单次执行模板 |
| **Q2** | "doFilterInternal 这个是 filter 里面的方法吗，只要重写了在 filter 周期会自动调用？改名成 doFilterInternal1 就永远不会被 Spring 执行，是这个意思吗" | 本文 §9.2「doFilterInternal 的归属与命名约束（实测）」——**不是 Filter 接口方法**，是 `OncePerRequestFilter` 的 abstract 方法；**改名后编译失败**（实测错误原文）；真正"不执行"是 bean 未注册/条件不满足/白名单 |
| **Q3** | "抽象类 钩子是什么意思，只要写 abstract 就会被自动调用吗，为什么它能自动调用，和前端知识不太一样" | 本文 §9.3「抽象类/钩子/为什么能自动调用」——`abstract` 只负责"强制实现"，**调用是父类模板方法里的一行普通代码**；附 20 行独立示例与实测输出；React 是可选钩子（运行期检查），Java 是抽象钩子（编译期契约），调用机制相同 |
| **Q4** | "那我怎么知道实现了它会在什么时机被调用？有什么地方可以查到？还是说这是 Spring Boot 框架机制封装的？" | 本文 §9.4「怎么知道钩子在什么时候被调用」——是封装但不是黑盒；**四种查证法**（打调用栈/看源码/官方 Javadoc/日志顺序）+ **实测调用栈**（`OncePerRequestFilter.doFilter:116 → doFilterInternal`）+ 常见扩展点时机表 + "谁调/在哪调/何时调"三问法 |
| **Q5** | "我怎么判断它是不是钩子呢，如果不知道它是钩子我就不会去实现那个方法，这样钩子也不会被调用不是吗" | 本文 §9.5「怎么判断是不是钩子、漏实现会怎样」——`javap` 列出 9 个方法（1 个 abstract 强制钩子 + 4 个可选钩子）；**强制钩子漏不了**（编译失败），**可选钩子漏了只是放弃能力**；发现方式 = 编译器 / IDE Override 列表 / 文档 + grep 现有实现 |
| **Q6** | "我怎么没有看到 interceptor 的相关知识，题目不是 filter 与 interceptor 深入吗，我只看到了 filter" | **学员发现课程内容缺失**（讲义只深入了 Filter）。已补 §四·补「Interceptor 深入」——三方法详解 / HandlerMethod 独有能力 / 拒绝响应两种方式 / 异步与 SSE / 深化对比矩阵 / 三个坑；并补实验：`@RequireRole` + `PermissionInterceptor`（403/200 实测，日志证明能读到方法名与注解读） |

### 9.1 为什么会自动注册（学员问答补讲，2026-10-21）

```text
① 继承 OncePerRequestFilter  → 获得"我是 Filter"的身份（实现了 jakarta.servlet.Filter）
② @Component                 → 成为 Spring 容器里的 bean
③ Spring Boot Web 初始化      → 把容器里所有【Filter 类型的 bean】自动包装成 FilterRegistrationBean，
                                注册进 Servlet 容器（默认 /* 全路径，顺序取 @Order）   ← ★ 关键环节
④ Tomcat 收到请求             → 按注册顺序调用每个 Filter → 你的 doFilterInternal
```

**三个要点**：

1. "被调用"的**直接原因**是第③步的自动注册（不是"继承"这个动作）——`@Order(1)` 的 TokenAuth 在 `@Order(2)` 的 BodyCaching 之前执行，就是注册时读 `@Order` 决定的；
2. `OncePerRequestFilter` 额外提供"同一个请求只执行一次"的模板（内部用 request 属性打标记，转发/异步派发时跳过），所以你只需实现 `doFilterInternal`；
3. 注册方式有三种：`@Component`（自动注册，全路径）、`FilterRegistrationBean`（手动，可指定 URL 模式与顺序）、`@WebFilter` + `@ServletComponentScan`（Servlet 原生）。

**前端类比**：`doFilterInternal` ≈ Express 的 `app.use((req,res,next) => { ...; next(); ... })`；`chain.doFilter` ≈ `next()`。

### 9.2 `doFilterInternal` 的归属与命名约束（2026-10-21 实测）

**它不是 `Filter` 接口的方法**——`Filter` 接口只有 `doFilter(ServletRequest, ServletResponse, FilterChain)`。
`doFilterInternal` 是 **`OncePerRequestFilter` 抽象类定义的 `abstract` 方法**，子类必须实现。

**模板方法模式（框架定骨架，你填钩子）**：

```java
// OncePerRequestFilter 内部（简化）：
public final void doFilter(req, res, chain) {        // ★ final：子类不能改
    if (已执行过) { chain.doFilter(req, res); return; }
    doFilterInternal(req, res, chain);               // ★ 调钩子（子类实现）
}
```

**实测：把方法名改成 `doFilterInternal1` → 编译直接失败**（`mvn clean compile` 原文）：

```text
[ERROR] TokenAuthFilter 不是抽象的，并且未覆盖 OncePerRequestFilter 中的抽象方法
        doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
[ERROR] 方法不会覆盖或实现超类型的方法
```

**结论：改名不是"运行期不执行"，而是"编译期就通不过"**——抽象方法的存在本身就是编译期契约。

**"永远不执行"的真实场景**（与改名无关）：

| 做法 | 效果 |
|---|---|
| 不加 `@Component` | 不是 bean → 不注册 → 不执行 |
| `@ConditionalOnProperty` 不满足 | bean 不存在（`TokenAuthFilter` 默认如此） |
| `shouldNotFilter` 返回 true | 注册了，但该请求被跳过（白名单） |
| 不调用 `chain.doFilter` | 执行了，但请求不继续往下走（拦截） |

> **对比 `Filter` 接口**：直接 `implements Filter` 时必须实现 `doFilter`——同样**方法名不能改**（接口契约）。两者都是"命名即契约"。

### 9.3 "抽象类 / 钩子 / 为什么能自动调用"（2026-10-21 学员追问，附独立示例）

**先破误解**：`abstract` **不负责调用**，它只做一件事——**强制子类必须实现**（不实现编译失败）。**调用**是父类模板方法里的一行普通代码干的。

**20 行独立示例（不依赖 Spring，可直接跑）**：

```java
abstract class BaseFilter {
    final void doFilter() {              // 流程固定，子类不能改
        System.out.println("① 父类 doFilter：被调用");
        doFilterInternal();              // ★ 父类主动调用钩子——自动调用的真正来源
        System.out.println("③ 父类 doFilter：结束");
    }
    abstract void doFilterInternal();    // 强制子类实现
}

class MyFilter extends BaseFilter {
    @Override
    void doFilterInternal() {
        System.out.println("② 子类 doFilterInternal：我填的钩子被执行了");
    }
}

public class HookDemo {
    public static void main(String[] args) {
        new MyFilter().doFilter();       // 只调 doFilter，没有任何代码调 doFilterInternal
    }
}
```

**实测输出**：

```text
① 父类 doFilter：被调用
② 子类 doFilterInternal：我填的钩子被执行了
③ 父类 doFilter：结束
```

**三条结论**：

1. **"钩子" = 父类预留空位 + 父类在固定时机调用 + 子类填内容**（像公司报销模板：流程固定，审批人由各部门填）；
2. **`abstract` 的作用是"强制实现"**（编译期契约），不是"自动调用"；
3. **"自动"的感觉来自"调用写在父类里"**——调用链是 `容器 → doFilter（父类 final）→ doFilterInternal（你的）`，一行普通的 Java 方法调用，没有魔法。

**三种钩子对比**：

| 类型 | 父类写法 | 子类必须实现？ | 谁调 |
|---|---|---|---|
| **抽象钩子** | `abstract void hook();` | ✅ 必须 | 父类 |
| 可选钩子 | `void hook() {}`（空体） | ❌ 可选 | 父类 |
| 接口方法 | `interface { void hook(); }` | ✅ 必须 | 调用方 |

**为什么和前端感觉不一样**：React 的 `componentDidMount` 是**可选钩子**（不写就跳过，运行期检查）；`doFilterInternal` 是**抽象钩子**（不写编译失败，编译期契约）。**调用机制完全相同**——React 内部也是 `component.componentDidMount?.()` 在固定位置调你。

**对照表**：

| | React | Java（OncePerRequestFilter） |
|---|---|---|
| 调用方 | React 的 mount 流程 | 父类的 `doFilter` |
| 调用点 | `componentDidMount?.()` | `doFilterInternal()` |
| 子类不实现 | 跳过（可选） | **编译失败**（abstract 强制） |

### 9.4 怎么知道钩子"会在什么时候被调用"（2026-10-21 学员追问）

**结论：是框架封装的，但不是黑盒——每层都有名字，可查证。**

| 层 | 做了什么 | 在哪查 |
|---|---|---|
| Servlet 规范 | 规定"容器必须让每次请求按顺序经过 Filter 链" | Servlet 规范 / Tomcat `ApplicationFilterChain` 源码 |
| Spring Boot | 把 `Filter` 类型 bean 自动注册进 Servlet 容器 | `ServletContextInitializerBeans` 源码 |
| `OncePerRequestFilter` | 模板方法：`doFilter` 第 116 行调 `doFilterInternal` | 类源码 |

**四种查证方法（按可靠度）**：

**① 打调用栈（最快）**——钩子里临时加一行，栈直接给出"谁调、第几行"：

```java
Thread.currentThread().getStackTrace()   // 或 new Exception("探针").printStackTrace()
```

**实测结果**（在 `TokenAuthFilter.doFilterInternal` 开头加探针，发一次请求）：

```text
TokenAuthFilter.doFilterInternal(TokenAuthFilter.java:41)                 ← 你的钩子
  org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)   ← ★ 父类第 116 行调用
  org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162)  ← Tomcat 的 Filter 链
  org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138)
  org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100)  ← 另一个框架 Filter
  OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
  ApplicationFilterChain.internalDoFilter(...)
```

**从下往上读**：Tomcat 的 `ApplicationFilterChain` 逐个调用 Filter → 轮到 `TokenAuthFilter` 时调的是父类 `OncePerRequestFilter.doFilter` → **父类第 116 行调用了 `doFilterInternal`**。

**② 看源码（最根本）**：IDE 里 `Ctrl + 点击` 类名跳到 `doFilter`，直接看第 116 行；或从 `.m2` 找 `spring-web-x.jar` 用 `javap -p -c` 反编译。

**③ 官方 Javadoc**：`OncePerRequestFilter` 类注释写明 *"guarantee a single execution per request dispatch"*——"per request dispatch"就是时机。

**④ 加日志看顺序**：打日志观察它与前后日志的先后（SP3 推 Filter → Interceptor 顺序就是这么做的）。

**常见扩展点"时机表"**：

| 扩展点 | 调用时机 | 谁调 |
|---|---|---|
| `@PostConstruct` | bean 属性注入完成后、使用前 | Spring 容器 |
| `ApplicationRunner.run` | 容器启动完成 | SpringApplication |
| **`Filter.doFilter`** | **每次 HTTP 请求进入**（DispatcherServlet 之前） | **Tomcat** |
| `HandlerInterceptor.preHandle` | 路由到 handler 后、执行前 | DispatcherServlet |
| `@EventListener` | 事件发布时 | Spring 事件机制 |
| `HealthIndicator.health()` | 每次访问 `/actuator/health` | Actuator |
| `BeanPostProcessor` | 每个 bean 初始化前后 | Spring 容器 |

**方法论（三问）**：遇到新接口/注解就问——① 谁调它？② 在哪调的（文件+行号/栈）？③ 什么时候调？**用栈和源码回答，不靠记忆。**

### 9.5 怎么判断"它是不是钩子"、漏实现会怎样（2026-10-21 学员追问）

**先看 `OncePerRequestFilter` 的完整钩子清单**（`javap -classpath spring-web-6.2.17.jar org.springframework.web.filter.OncePerRequestFilter`）：

```text
public void doFilter(...)                              ← 框架入口（不建议覆写）
protected boolean isAsyncDispatch(...)                 ← 内部用
protected boolean isAsyncStarted(...)                  ← 内部用
protected String getAlreadyFilteredAttributeName()     ← 可覆写（改标记名）
protected boolean shouldNotFilter(HttpServletRequest)  ← ★ 可选钩子
protected boolean shouldNotFilterAsyncDispatch()       ← ★ 可选钩子
protected boolean shouldNotFilterErrorDispatch()       ← ★ 可选钩子
protected abstract void doFilterInternal(...)          ← ★ 强制钩子（abstract）
protected void doFilterNestedErrorDispatch(...)        ← 可选钩子
```

**钩子分两类，风险完全不同**：

| 类型 | 例子 | 不实现时 | 你会不会"不知道" |
|---|---|---|---|
| **强制钩子（abstract）** | `doFilterInternal` | **编译失败** | 不可能（编译器逼你实现） |
| **可选钩子（普通 protected 方法）** | `shouldNotFilter`、`WebMvcConfigurer.addInterceptors` | 编译通过、**静默不生效** | 可能漏（真实风险） |

**判断"是不是钩子"的三个来源**：

1. **编译器**：`abstract` 方法不实现就报错——强制钩子不用判断，编译器替你判断；
2. **IDE 的 Override 列表**：`Ctrl + O` 列出所有可覆写方法 = **钩子清单**（命令行版就是上面的 `javap`）；
3. **官方文档 + 现有代码**：`grep` 项目里的 `implements .*Configurer` / `extends .*Filter` / `@Aspect` / `@EventListener`，就是系统用过的钩子现场。

**关键认知**：

- **强制钩子漏不了**（编译期保证）；
- **可选钩子可能漏，但那是"放弃能力"，不是"程序坏了"**——框架设计上它们都是可选的；
- **钩子按需查找，不用背全**：需求驱动（"要做登录校验"→ 查 Spring 怎么做 → 找到 Filter 钩子），而非通读源码。
- **需要主动盘点钩子的只有两个场景**：接手项目（grep 实现类）、排查"某功能不生效"（查对应钩子→是否实现/条件是否满足/是否被 shouldNotFilter 挡）。

**本项目的实例**：`TokenAuthFilter` 只实现了 2 个钩子（`doFilterInternal` 强制 + `shouldNotFilter` 可选），其余 4 个可选钩子未实现——功能完全正常。
