# SP3 · MVC 请求处理全流程

> **课程**：Spring 基础实战 · 模块一（2026-10-21）
> **已会**：场景① Controller / 参数校验 / 全局异常（[①统一API规范](../业务场景实战/①统一API规范.md)）
> **本次**：一次请求从进门到出门的**完整旅程**、**Filter vs Interceptor vs AOP 对比矩阵**（三者的位置与选型）、参数解析器背后、返回值与内容协商
> **对标**：廖雪峰 22.4「开发 Web 应用」
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **场景① 统一 API 规范**（[①统一API规范](../业务场景实战/①统一API规范.md)）：所有接口返回统一的 `ApiResponse` 结构；参数用 `@Valid` + Bean Validation 校验；异常由 `@RestControllerAdvice` 全局兜底成统一错误体；
- **SP2 AOP**：切面在**方法调用**这一层插入逻辑（比 Interceptor 更靠近业务）；
- **J1**：Controller 只是普通类 + 注解，方法参数由框架按规则填值。

---

## 一、一次 HTTP 请求的完整旅程

```text
浏览器/curl
   ↓
【Servlet 容器】Tomcat（线程池分配线程）
   ↓
【Filter 链】javax/jakarta.servlet.Filter（项目里 CORS 就在这层）
   ↓
【DispatcherServlet】Spring MVC 的总调度室（唯一入口）
   ↓ ① HandlerMapping：按 URL 找到 Controller 方法
   ↓ ② HandlerAdapter：选一个合适的"适配器"来执行
   ↓ ③ 【Interceptor.preHandle】拦截器前刀（可拦截返回 false）
   ↓ ④ 参数解析：把 HTTP 报文变成方法参数（@RequestParam/@PathVariable/@RequestBody + 校验）
   ↓ ⑤ Controller 方法执行（你的业务代码）
   ↓ ⑥ 返回值处理：@ResponseBody → HttpMessageConverter → JSON
   ↓ ⑦ 【Interceptor.postHandle】后刀（Controller 正常返回后）
   ↓ ⑧ 【Interceptor.afterCompletion】收尾刀（无论成败，一定执行）
   ↓
【Filter 链】离开
   ↓
Tomcat 写回 HTTP 响应
```

**三条关键边界**（本课核心）：

| 层 | 能拿到什么 | 典型用途 |
|---|---|---|
| **Filter** | 原始 `HttpServletRequest/Response`（**不知道 Controller 方法**） | 编码、CORS、请求体包装、全链路 traceId |
| **Interceptor** | `HandlerMethod`（知道要执行哪个 Controller 方法）+ request/response | 登录校验、权限、请求日志（能拿到方法名） |
| **AOP** | 方法参数/返回值（**不知道 HTTP**） | 业务级审计（场景⑤）、事务、缓存 |

**一句话**：**Filter 管"报文"，Interceptor 管"路由"，AOP 管"方法"**——越往后越接近业务。
---

## 二、DispatcherServlet：总调度室的五个关键组件

| 组件 | 职责 | 前端类比 |
|---|---|---|
| **HandlerMapping** | URL → Controller 方法 | 路由表（`{ path: '/api/tasks', handler }`） |
| **HandlerAdapter** | 调用目标方法的适配器 | 适配不同函数签名 |
| **HandlerExceptionResolver** | 异常 → 响应 | 全局错误兜底（场景1 的 @RestControllerAdvice 最终由它驱动） |
| **HttpMessageConverter** | 报文 ↔ Java 对象 | JSON.parse / JSON.stringify（@RequestBody / @ResponseBody 背后） |
| **ViewResolver** | 视图名 → 模板 | 前后端分离项目用不到（忽略） |

**为什么需要 HandlerAdapter**：Controller 方法签名千变万化（参数类型/个数不同），适配器负责"知道每个方法怎么调用"。

---

## 三、参数绑定的背后

```java
@GetMapping("/api/tasks")
public ApiResponse<List<TaskSummaryResponse>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) TaskStatus status) { ... }
```

| 注解 | 取值来源 | 背后机制 |
|---|---|---|
| `@RequestParam` | URL query（?page=1） | 类型自动转换（String→int→枚举） |
| `@PathVariable` | 路径模板（/tasks/{taskId}） | 同上 |
| `@RequestBody` | 请求体 JSON | **HttpMessageConverter**（Jackson）反序列化 |
| `@Valid` | —— | 触发 Bean Validation（场景1），失败抛异常→全局处理器转 400 |
| 无注解简单类型 | 按名字从 query 取 | **隐式绑定，不推荐**（改参数名就坏） |

**参数解析器**：`HandlerMethodArgumentResolver` 家族——每个注解对应一个解析器，Spring 内置 20+ 个。想支持自定义参数（如当前登录用户），实现一个解析器即可（进阶）。

---

## 四、★ Filter vs Interceptor vs AOP 对比矩阵（本课核心）

| 维度 | Filter | Interceptor | AOP |
|---|---|---|---|
| 所属 | Servlet 规范 | Spring MVC | Spring AOP |
| 位置 | DispatcherServlet **之前** | DispatcherServlet **之内**、Controller 前后 | Controller/Service **方法调用层** |
| 能拿到 | 原始 request/response（**不知道 Controller**） | **HandlerMethod**（知道目标方法）+ request/response | 方法参数/返回值（**不知道 HTTP**） |
| 能否改响应 | 可以包装 response | 能力有限 | 不能改 HTTP 响应 |
| 执行时机 | 最早 | 路由之后 | 方法调用前后 |
| 典型用途 | 编码 / CORS / 请求体包装 / traceId | 登录校验 / 权限 / 请求日志 | 事务 / 审计 / 缓存 |
| 项目实例 | `WebCorsConfiguration`（CORS） | 无（本课要加） | `AuditAspect`（场景5） |

**选型口诀**：**要改报文用 Filter，要看路由用 Interceptor，要管方法用 AOP**。

**预期执行顺序**（§六 实验实测）：

```text
Filter 进入 → Interceptor.preHandle → Controller → Interceptor.postHandle
→ Interceptor.afterCompletion → Filter 离开
```

---

## 五、返回值与内容协商

- `@RestController` = `@Controller` + `@ResponseBody`：返回值交给 HttpMessageConverter 序列化（JSON）；
- **内容协商**：客户端 `Accept: application/json` → Jackson 转换器；`Accept: text/html` → 视图（REST 项目极少用）；
- **统一返回体**（场景1 `ApiResponse<T>`）：所有接口 `{code, message, data}` 同一结构——前端只写一套解析、错误处理只写一处；
- 序列化细节：`@JsonFormat` 控制时间格式；枚举默认序列化成 `name()`（`"WAITING_FOR_CONFIRMATION"`）。

---

## 六、实验记录（2026-10-21 实测：GET /api/tasks）

实验载体：`infrastructure/web/` 下的 `RequestTraceFilter`（Filter）+ `RequestTraceInterceptor`（Interceptor）+ `WebRequestTraceConfiguration`（注册）。教学演示代码，可删。

```text
[SP3-Filter] 进入：GET /api/tasks
[SP3-Interceptor] preHandle：handler = ResearchTaskController#listTasks
[SP3-Interceptor] postHandle（Controller 已正常返回，视图渲染前）
[SP3-Interceptor] afterCompletion（请求完成，无论成败；异常=无）
[SP3-Filter] 离开：耗时 419 ms，status=200
```

**四条实证结论**：

1. **Filter 最早进、最晚出**（419ms 是它量到的整条链路耗时）；
2. **Interceptor 知道 `ResearchTaskController#listTasks`**——**Filter 拿不到这个信息**（这就是"登录校验/权限"放 Interceptor 而不是 Filter 的根本原因）；
3. **postHandle 只在正常返回时执行**；抛异常时 **afterCompletion 仍会执行**（且能拿到异常）——清理类逻辑必须放 afterCompletion；
4. 一个 `WebMvcConfigurer` 还不够？项目已有 `WebCorsConfiguration`，本课又加了一个——**多个实现是叠加生效的，不会互相覆盖**。

### 6.1 本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| [`RequestTraceFilter.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/RequestTraceFilter.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/RequestTraceFilter.java) | 请求追踪 Filter（Servlet 层，测全链路耗时） | ✅ 可删 |
| [`RequestTraceInterceptor.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/RequestTraceInterceptor.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/RequestTraceInterceptor.java) | 请求追踪 Interceptor（preHandle/postHandle/afterCompletion 三时机日志） | ✅ 可删 |
| [`WebRequestTraceConfiguration.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/WebRequestTraceConfiguration.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/web/WebRequestTraceConfiguration.java) | 注册 Interceptor 到 `/api/**` | ✅ 可删 |

> 成组删除：`WebRequestTraceConfiguration` 引用 `RequestTraceInterceptor`，单独删会编译失败。

---

## 七、项目关联地图

| 概念 | 项目位置 |
|---|---|
| Controller 全家桶 | `ResearchTaskController`（@RestController + @Valid + 统一返回体） |
| 全局异常 | `ApiExceptionHandler`（@RestControllerAdvice，场景1） |
| CORS（Filter/Config 层） | `WebCorsConfiguration` |
| 请求日志 | `RequestTraceFilter` / `RequestTraceInterceptor`（SP3 演示） |
| 方法级增强 | `AuditAspect`（SP2 / 场景5） |

---

## 八、思考题（自查 + 参考答案）

1. 登录校验应该放 Filter 还是 Interceptor？为什么 Filter 拿不到"目标方法"会成为问题？<br>
   **参考答案**：放 **Interceptor**。因为细粒度权限需要知道"**要执行哪个 Controller 方法**"（例：方法上标了 `@RequireRole("ADMIN")`，或按方法做审计/限流），而 Filter 只知道 URL——参数化路径（`/tasks/{id}`）难以精确对应到方法。本次实验实测：Interceptor 能打印 `ResearchTaskController#listTasks`，Filter 拿不到。**但全局性拦截**（非法 IP、全站未登录）用 Filter 更早、更省。
2. 一个请求在 Controller 里抛了异常：preHandle / postHandle / afterCompletion 哪个执行了、哪个没执行？<br>
   **参考答案**：`preHandle` 执行了；**`postHandle` 不执行**（只有 Controller 正常返回才走）；**`afterCompletion` 会执行**，且入参能拿到异常。**结论：资源清理、计时、日志收尾必须放 afterCompletion**。
3. 想把请求体（JSON）在进入 Controller 前脱敏，为什么必须在 Filter 层做（提示：流只能读一次）？<br>
   **参考答案**：Servlet 的 `request.getInputStream()` 是**一次性流**，读完即空——Controller 的 `@RequestBody` 也要读它。在 Filter 里直接读会把流耗掉，Controller 拿不到 body。正确做法：**包装 request**（`HttpServletRequestWrapper` 子类，构造时缓存 body 字节，覆写 `getInputStream()` 返回可重复读的流），再把包装对象传给 `chain.doFilter`。这也是"改报文只能在 Filter 做"的由来。
4. 多个 Interceptor 时顺序怎么定？（提示：`registry.addInterceptor` 的注册顺序 + `@Order`）
   **参考答案**：按 `registry.addInterceptor(...)` 的**注册顺序**执行：`preHandle` **正序**，`postHandle` / `afterCompletion` **逆序**（像栈）。也可以用 `registry.addInterceptor(x).order(n)` 显式指定；跨配置类的先后可用 `@Order`。**设计建议**：与业务无关的（traceId）放最外层，权限放中间，业务日志放最内层。

---

## 九、本课问答记录（2026-10-21）

（待补——学员提问后按双台账纪律记录）
