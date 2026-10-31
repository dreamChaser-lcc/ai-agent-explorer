# T4 · ErrorEnum：错误码枚举化

> **状态**：✅ 学习中（2026-10-31，枚举 + ApiError 入口 + 5 处示范改造）
> **前置**：[J6·枚举](../Java基础实战/J6·枚举.md)（枚举的隐藏机制）/ [J4·异常体系](../Java基础实战/J4·异常体系.md)（统一错误响应）
> **上一课**：[T1·Lombok](T1·Lombok.md)
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单第 2 项：ErrorEnum 错误码）

---

## 一、改造前：散落的字符串（实测 11 处）

```java
// ApiExceptionHandler 里各 handler 各自写字符串
return ApiError.of("TASK_NOT_FOUND", exception.getMessage(), path);
return ApiError.of("DATA_ACCESS_ERROR", "数据访问失败：" + ..., path);
return ApiError.of("CONCURRENT_CONFLICT", "数据已被其他操作修改...", path);
return ApiError.of("INTERNAL_ERROR", "服务器内部错误", path);
// ... 共 11 个不同的 code

痛点：
① 拼写错误编译期查不出（"TASK_NOT_FOND" 只有运行时/前端才发现）
② 没有一处能看全"项目到底有哪些错误码"（散在 handler 里）
③ HTTP 状态码与 code 在 handler 里各自配对 —— 容易配错（同 code 不同状态）
```

## 二、ErrorCode 枚举（三要素设计）★

```java
public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数校验失败", HttpStatus.BAD_REQUEST),
    TASK_NOT_FOUND("TASK_NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND),
    CONCURRENT_CONFLICT("CONCURRENT_CONFLICT", "数据已被其他人修改...", HttpStatus.CONFLICT),
    // ... 共 11 个

    private final String code;          // 对外契约（★ 保持与旧值一致 = 不破坏前端）
    private final String message;       // 默认人类可读消息
    private final HttpStatus httpStatus; // HTTP 语义（集中映射）
}
```

**三要素的分工**：

| 字段 | 谁用 | 稳定性 |
|---|---|---|
| `code` | 前端判断（if code === "TASK_NOT_FOUND"） | **绝对稳定**（改名 = 破坏契约） |
| `message` | 人看（可被上下文化覆盖） | 可演进 |
| `httpStatus` | 对账/文档/新 handler 参考 | 稳定（语义绑定） |

## 三、ApiError 加枚举入口（兼容并存）

```java
// 旧（保留）：String 版 —— 渐进迁移期间不删
public static ApiError of(String code, String message, String path)

// 新（T4）：枚举版
public static ApiError of(ErrorCode errorCode, String path)                    // 用默认消息
public static ApiError of(ErrorCode errorCode, String message, String path)    // 上下文覆盖
```

## 四、handler 改造示范（5 处）

```diff
-ApiError.of("TASK_NOT_FOUND", exception.getMessage(), request.getRequestURI())
+ApiError.of(ErrorCode.TASK_NOT_FOUND, exception.getMessage(), request.getRequestURI())

-ApiError.of("DATA_ACCESS_ERROR", "数据访问失败：" + ..., request.getRequestURI())
+ApiError.of(ErrorCode.DATA_ACCESS_ERROR, "数据访问失败：" + ..., request.getRequestURI())

-ApiError.of("INTERNAL_ERROR", "服务器内部错误", request.getRequestURI())
+ApiError.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI())   ← 用枚举默认消息
```

**关键：对外 code 字符串一个没变**（"TASK_NOT_FOUND" 还是 "TASK_NOT_FOUND"）——前端零感知，契约零破坏。

**验证**：`mvn clean compile` 通过（COMPILE=0）；剩余 7 处 String 版（其他错误码）按渐进式留待迭代。

## 五、渐进式迁移约定（与 T1 的决策一致）

```text
不改的：剩余 7 处 ApiError.of("XXX", ...) —— 功能正常，专项批量改是纯风险
要改的：新写 handler 优先用 ApiError.of(ErrorCode.XXX)；
        迭代既有 handler 时顺手把字符串换成枚举
```

## 六、进一步演进（企业级方向，暂不做）

```text
① 错误码分段（模块前缀）：
   1xxxx = 通用 / 2xxxx = 任务 / 3xxxx = 报告 —— 大规模项目才需要
② i18n：message 走 MessageSource（code 不变、语言可变）
③ 码表导出：编译期生成 JSON 给前端（做常量文件/文档）
④ 与 OpenAPI 联动：@ApiResponse 里引用错误码枚举（文档即码表）
```

## 七、面试速答

```text
Q：错误码用枚举还是常量类？
A：枚举。类型安全（编译期查错）+ 可挂多字段（message/httpStatus）+ 可遍历（码表/文档）。
   常量类只能给字符串，且无法阻止"传错常量"。

Q：错误码和 HTTP 状态码什么关系？
A：两层语义。HTTP 状态 = 粗粒度协议语义（400/404/409/500 告诉框架/网关/监控怎么办）；
   code = 细粒度业务语义（同 409 可能是"版本冲突"或"重复提交"）
   → 1 个 HTTP 状态可以对应多个 code，前端靠 code 精确判断。

Q：code 能改吗？
A：绝对不能（对外契约）。改语义 → 加新码 + 旧码标 @Deprecated（保留一个版本周期）。
```

## 一句话

```text
错误码 = 对外契约的字符串字典 —— 用枚举集中管理（code/message/httpStatus 三合一）
改造的关键是"零契约破坏"：code 值不变、String 版并存、渐进替换
拼写错误编译期揪出、码表一处看全、状态语义成对 —— 这就是枚举化的全部收益
```
