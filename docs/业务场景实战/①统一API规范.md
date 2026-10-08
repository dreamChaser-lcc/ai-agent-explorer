# ① 统一 API 规范

> **状态**：✅ 完成（2026-10-07 开课 → 2026-10-08 验证闭环）
> **带出知识**：MVC 切面、异常体系、HTTP 语义（回查：J4 异常体系 / J7 注解体系 / J2 分派规则）
> **闭环**：审计 → 抓现场 → 补丁 → 再验证 → **固化（含产出：API 设计规范）** ✓

---

## 1. 开课动机

**为什么"统一 API 规范"是地基型场景**：所有前端/客户端都依赖它——响应结构一致、错误可预期，前端才能写出一个靠谱的"请求拦截器/错误处理器"。规范一旦松动，所有调用方都要写特判。

**本项目现状**：底子已经很硬（J1 的 `ApiError` + J4 的"值班室"都是此前的成果）——所以本课不是"从零搭建"，而是：**审计 → 找漏网之鱼 → 补齐 → 固化成文档**。

---

## 2. 现状审计（2026-10-07，读码完成）

### 2.1 成功响应

| 端点 | 返回 | 状态码 |
|------|------|--------|
| `POST /api/tasks` | `TaskSummaryResponse`（裸 DTO） | **201 Created** |
| `GET /api/tasks` | `List<TaskSummaryResponse>` | 200 |
| `GET /api/tasks/{id}` | `TaskDetailResponse` | 200 |
| `GET /api/tasks/{id}/executions` `/events` | `List<...Response>` | 200 |
| `POST .../pause` `.../resume` `.../cancel` `.../confirm` | `void` | **204 No Content** |
| `GET .../stream` | `SseEmitter`（SSE 特殊端点） | 200 |
| `GET /api/health` | `Map<String, Object>` | 200 |

**结论**：采用的是"**纯 REST 流派**"——裸 DTO + HTTP 状态码语义（不套 `{code, message, data}` 包装壳）。这是成立的设计（现代 REST 推荐），**记录为规范第一条**。

### 2.2 错误响应与异常覆盖（`ApiExceptionHandler`）

| 异常 | HTTP | 错误码 | 要点 |
|------|------|--------|------|
| `TaskNotFoundException` | 404 | `TASK_NOT_FOUND` | 业务异常 |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | ★ 带字段明细（fields） |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST` | 坏 JSON / 枚举取值非法 |
| `IllegalStateException` | 400 | `INVALID_STATE` | 状态不合法 |
| `Exception`（兜底） | 500 | `INTERNAL_ERROR` | 日志留堆栈、响应不泄密 |

统一错误结构 = **`ApiError`**（code / message / path / timestamp / fields / retryable）。

### 2.3 参数校验

- JSR-303（`@NotBlank` / `@NotNull` + 中文 message）；
- 触发点：Controller 方法参数上的 `@Valid`；
- 失败 → `MethodArgumentNotValidException` → 400 + 字段明细。

### 2.4 审计结论

```text
管理得住：业务异常（你代码抛的）
可疑区域：框架级异常（Spring 在到达你的 handler 之前就拦下的）★ 见下
```

---

## 3. 缺口假设 → ★ 被现场推翻 → 真实缺口

### 3.1 初始假设（2026-10-07）

| # | 场景 | 疑犯异常 | 预期状态码 |
|---|------|---------|-----------|
| 1 | 用 GET 打 POST 端点（如 `/pause`） | `HttpRequestMethodNotSupportedException` | 405 |
| 2 | 请求体 Content-Type 不对 | `HttpMediaTypeNotSupportedException` | 415 |
| 3 | 路径变量给的不是 UUID（`/api/tasks/abc`） | `MethodArgumentTypeMismatchException` | 400 |
| 4 | 访问不存在的路径 | 默认 404 处理 | 404 |

**假设**：这些情况会**漏出 Spring 默认错误结构**（没有 code 字段）→ 前端无法统一处理。
**为什么必须验证**：Spring 版本行为有差异——**不许猜，跑一遍**。

### 3.2 ★ 抓现场：假设被推翻（4 × 500）

实测结果：**四种情况全部返回 500 `INTERNAL_ERROR`**——**没有**"漏出默认结构"，而是全被**兜底 handler**（`@ExceptionHandler(Exception.class)`）接住了。

### 3.3 真实缺口：兜底 handler 太"贪吃"

分派规则（J2 知识）：异常匹配多个 handler 时**取最具体**——但表里**没有"具体"的**框架异常 handler，于是全落进最宽泛的 `Exception` 兜底 → **统一误报 500**。

**三层伤害**：

| # | 伤害 | 说明 |
|---|------|------|
| 1 | **语义错位** | "你参数写错了"（4xx，用户可修正）被说成"我服务器坏了"（5xx）——归因错误，误导排障 |
| 2 | **污染前端/监控** | 前端收到 500 触发上报/告警/系统错误页；监控 500 曲线飙升——而实际没有真故障 |
| 3 | **日志假警报** | 每个歪请求打一条 ERROR 堆栈——运维视角"服务器全是错" |

**★ 本课最大的元收获——"用证据说话"现场课**：如果按 3.1 的假设**直接写代码**，补丁方向会全偏（去修"默认结构一致化"）——**真实系统只有跑起来才说真话**。假设是探照灯，不是答案。

---

## 4. 抓现场记录（2026-10-08，实测）

### 4.1 补丁前实测（4 × 500）

| # | 请求 | 实际 | body |
|---|------|------|------|
| 1 | `/api/tasks/not-a-uuid` | **500** | `{"code":"INTERNAL_ERROR",...}` |
| 2 | `GET .../pause` | **500** | 同上 |
| 3 | `POST /api/tasks`（text/plain） | **500** | 同上 |
| 4 | `/api/nonexistent-path` | **500** | 同上 |

### 4.2 日志证据（异常真实类名）

```text
未处理异常: GET /api/tasks/not-a-uuid
org.springframework.web.method.annotation.MethodArgumentTypeMismatchException   ← 嫌疑人 1

未处理异常: GET /api/tasks/{id}/pause
org.springframework.web.HttpRequestMethodNotSupportedException                 ← 嫌疑人 2
```

（另两条标准类名：`HttpMediaTypeNotSupportedException` / `NoResourceFoundException`——后经补丁实测确认 ✓）

### 4.3 实战插曲：启动连环坑（两条排障经验）

| 坑 | 现象 | 真因 | 解法 |
|----|------|------|------|
| ① profile 蒸发 | `Unable to determine Dialect...set 'jdbc.url'` | 新窗口里 `SPRING_PROFILES_ACTIVE` 没设（**进程级变量关窗即失**） | 重设 env 或配置 IDE Run Configuration |
| ② H2 文件锁 | `Database may be already in use` / `The file is locked: research-agent.mv.db` | **旧 java 孤儿进程**占着 H2 文件（嵌入模式单进程独占；叉窗口 ≠ 优雅停机） | `jps -l` 找 → `Stop-Process -Id <PID> -Force` |

---

## 5. 补丁（2026-10-08，已写入 `ApiExceptionHandler`）

**新增 4 个"更具体"的 handler**（分派规则下优先于兜底命中）：

| handler 捕获 | HTTP | 错误码 | message 示例 |
|-------------|------|--------|-------------|
| `MethodArgumentTypeMismatchException` | 400 | `INVALID_PARAMETER` | "参数格式错误：taskId" |
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | "请求方法不支持：GET" |
| `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | "请求体类型不支持，请使用 application/json" |
| `NoResourceFoundException` | 404 | `NOT_FOUND` | "请求的资源不存在" |

**设计要点**：① 全部返回 `ApiError` 统一结构；② 错误码用"名词短语"风格（与既有 `TASK_NOT_FOUND` 一致）；③ **不动兜底**——它继续兜"真意外"（但不再被滥用）。

---

## 6. 验证记录（2026-10-08，重测通过）

| # | 请求 | 修复前 | **修复后** | 错误码 |
|---|------|--------|-----------|--------|
| 1 | `/tasks/not-a-uuid` | 500 | ✅ **400** | `INVALID_PARAMETER` |
| 2 | `GET /pause` | 500 | ✅ **405** | `METHOD_NOT_ALLOWED` |
| 3 | text/plain POST | 500 | ✅ **415** | `UNSUPPORTED_MEDIA_TYPE` |
| 4 | 野路径 | 500 | ✅ **404** | `NOT_FOUND` |

**附加验证**：日志里**不再出现**新的 `未处理异常` ERROR——"日志假警报"一并修掉（4xx 不再走兜底，自然不打 ERROR）。

---

## 7. ★ 固化：《API 设计规范》（本场景产出）

### 7.1 成功响应约定

- 资源/列表/详情：**裸 DTO**（`XxxResponse`），**不套包装壳**；
- 创建：**201** + 返回资源摘要；控制类操作：**204** 无 body；
- 时间字段：ISO-8601（`OffsetDateTime`）；字段命名：camelCase。

### 7.2 状态码使用表

| 状态码 | 语义 | 本项目使用 |
|--------|------|-----------|
| 200 | 成功 | 查询 |
| 201 | 已创建 | POST 创建任务 |
| 204 | 成功无内容 | pause/resume/cancel/confirm |
| 400 | 请求有误（可修正） | 校验/坏 JSON/状态非法/参数格式 |
| 404 | 找不到 | 任务不存在 / 路径不存在 |
| 405 | 方法不支持 | 用错 HTTP 方法 |
| 415 | 媒体类型不支持 | Content-Type 不是 JSON |
| 500 | 服务端意外 | 兜底（日志留痕、响应脱敏） |

### 7.3 错误码清单（`ApiError.code`）

| code | HTTP | 触发场景 |
|------|------|---------|
| `TASK_NOT_FOUND` | 404 | 任务不存在 |
| `VALIDATION_ERROR` | 400 | 字段校验失败（带 fields 明细） |
| `MALFORMED_REQUEST` | 400 | JSON 解析失败 / 枚举非法取值 |
| `INVALID_STATE` | 400 | 当前状态不允许该操作 |
| `INVALID_PARAMETER` | 400 | 参数格式错误（类型转换失败）★ 10-08 新增 |
| `METHOD_NOT_ALLOWED` | 405 | 请求方法不支持 ★ 10-08 新增 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 请求体类型不支持 ★ 10-08 新增 |
| `NOT_FOUND` | 404 | 路径不存在 ★ 10-08 新增 |
| `INTERNAL_ERROR` | 500 | 未预料异常（兜底） |

### 7.4 校验约定

- Controller 参数 `@Valid` + JSR-303 注解（message 用中文人话）；
- 失败必带 `fields`（字段名 + 原因）——前端可直接标红表单。

### 7.5 原则

1. **4xx = 客户端可修正；5xx = 服务端真问题**——不许错位；
2. **所有错误体必须是 `ApiError` 结构**——前端只需写一套错误处理；
3. **兜底只兜"真意外"**——框架异常必须给具体 handler（本课教训：`catch Exception` 太宽会吞掉语义）；
4. 5xx 的响应**不泄露内部细节**（堆栈只进日志——J4"内外有别"）。

---

## 8. 复盘与关联地图

**复盘收获**：

1. **用证据推翻假设**——原假设（漏出默认结构）被实测（全 500）推翻，真实缺口（兜底贪吃）更隐蔽——这是本场景最有价值的一课；
2. **分派规则"具体优先"实战**（J2 的知识第一次用到生产代码的修复上）；
3. **兜底的反面教训**——"兜底"是安全网不是垃圾桶，太宽的 catch 会吞掉正确语义；
4. **排障工具箱入库**——`jps` / `netstat -ano | findstr :PORT` / `Stop-Process`（启动连环坑的产出）。

**关联课程**：

| 课 | 关联点 |
|----|--------|
| J4 | "值班室"模式（本场景主角 `ApiExceptionHandler` 的来历） |
| J7 | 注解体系（`@ExceptionHandler` / `@ResponseStatus` 的读者是 Spring 请求期）；操作全谱第 ⑤ 类"卡表"扩展 |
| J2 | 分派规则"最具体优先"；条件装配思想 |
| J9 | 启动排障（profile/环境变量/H2 锁） |
