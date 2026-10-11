# ⑤ AOP 实操——操作审计日志（谁、何时、改了什么）

> **日期**：2026-10-11　**教材**：`@AuditLog`、`AuditAspect`、`AuditLogService`、`audit_log` 表
> **前置**：J7（注解 + 反射）、J2（CGLIB 代理戏服）、J8（并发）｜**后续**：场景⑥（异步升级 Kafka）

## 1. 开课动机

某天早上产品经理问：**"这个任务昨晚被取消了——谁干的？"**
——现在的你只能"翻服务器日志大海捞针"。**审计日志**解决的就是这个问题：

| 价值 | 场景 |
|------|------|
| **排查** | "谁取消的？""什么时候改的？"——可查询的操作记录 |
| **合规** | 敏感操作必须留痕（金融/医疗的硬性要求——"可审计"） |
| **复盘** | 谁在何时高频操作、哪些操作常失败（行为洞察） |

**盘点"值得留痕"的 5 个操作**（教材）：

```text
ResearchTaskCommandService：createTask
ResearchTaskControlService：confirmPlan / pauseTask / resumeTask / cancelTask
```

## 2. 现状审计（三处证据）

| # | 证据 | 说明 |
|---|------|------|
| 1 | `@Aspect` 全项目 **0 处** | 无任何 AOP 代码——又要"亲手造一件"（继 J7 自定义注解之后） |
| 2 | `pom.xml` **无 `spring-boot-starter-aop`** | 连依赖都要新加 |
| 3 | schema 的 8 张表里**没有审计表** | 要新建**第 9 张表** `audit_log` |

> 易混点厘清：`task_event_log` **不是**审计——它是"业务事件流"（任务发生了什么——给前端时间线看）；`audit_log` 是"责任审计流"（谁按的按钮——给排查/合规用）。**互补，不重复。**

## 3. 为什么不"每个方法里复制一遍"——四宗罪

```java
public void pauseTask(UUID taskId) {
    auditService.record("PAUSE_TASK", taskId, "demo-user");   // 直觉派的写法
    ...业务逻辑...
}
```

| # | 罪行 | 说明 |
|---|------|------|
| 1 | **重复** | 同一段逻辑复制 5 份——第 6 个操作来了再复制（维护成本 = 操作数 × 变更次数，**乘法**） |
| 2 | **侵入** | 业务方法被"杂事"污染 |
| 3 | ★ **漏网** | 新方法忘写——**审计静默缺失**——**"以为有审计"比"没有审计"更危险**（排查到一半才发现盲区） |
| 4 | **改不动** | 审计格式一变（加字段）——N 处全改 |

——审计是典型的"**横切关注点**"（cross-cutting concern）：**不属于任何单个业务，却横穿所有操作**。**"横穿"之事，用"切面"解决。**

## 4. ★ 知识课一：AOP——"横切"的艺术

### 4.1 一张图

```text
   业务方法（纵向）                    横切关注点（横向）：
   createTask  ─┐
   confirmPlan ─┤
   pauseTask   ─┼──── ✂️ "审计"穿过【所有】操作的一刀
   resumeTask  ─┤
   cancelTask  ─┘
   AOP 的目标：把这一刀【声明式】地切进去——业务代码零改动
```

### 4.2 前端锚点（早就用过 AOP 的思想）

| 前端 | 后端 AOP |
|------|---------|
| **Axios 拦截器**（所有请求自动过一道） | 切面（所有方法自动过一道）★ 最贴切 |
| **TS 装饰器**（`@Log()` 贴方法） | **注解 + 切面**（几乎一样） |
| **Express/Koa 中间件** | Filter/Interceptor/切面（同族） |

### 4.3 术语五件套

| 术语 | 人话 | 本项目 |
|------|------|--------|
| **Aspect 切面** | "外包工"本体 | `AuditAspect` |
| **Pointcut 切点** | 下刀的选择标准 | "贴了 @AuditLog 的方法" |
| **Advice 通知** | 刀法（什么时候动刀） | `@Around` 环绕 |
| **JoinPoint 连接点** | 可下刀的位置（方法调用时刻） | 运行时传入——`proceed()` 放行 |
| **Weaving 织入** | 把切面缝进代码 | Spring 用**代理**织入（J2 的 CGLIB 戏服） |

### 4.4 机制全景（"申请书 → 外包工 → 戏服"的实物化）

```text
你写：  @AuditLog("PAUSE_TASK") public void pauseTask(...)
启动时：Spring 读到注解 → 给 ResearchTaskControlService 造【代理戏服】
运行时：你调 pauseTask → 实际先撞进【代理】→ 切面（记"开始"）
                          → proceed() 放行 → 真方法
                          → 切面（记"成功/失败"）
```

### 4.5 恍然大悟：`@Transactional` 就是 AOP

```text
@Transactional → 代理 → 方法前后插入"开事务/提交/回滚"
@AuditLog      → 代理 → 方法前后插入"写审计记录"
——同一套机制，不同的"刀法"。
```

## 5. 设计抉择

### 5.1 载体：第 9 张表 `audit_log`（append-only）

| 字段 | 含义 |
|------|------|
| `actor` | **谁**（MVP 固定 demo-user；正式方案 = 用户/安全上下文） |
| `action` | **干了什么**（来自注解 value） |
| `target_type` / `target_id` | 操作对象（RESEARCH_TASK + taskId） |
| `result` | **SUCCESS / FAILED**——★ 失败也要记 |
| `error_message` / `detail` | 失败原因 / 扩展细节 |
| `created_at` | **何时**（+ 索引：按时间倒序查） |

> **不继承 BaseTimeEntity**（那是"可变记录"基类带 updated_at）——审计**只插入不更新**——与 `task_event_log` 同风格。

### 5.2 三件套（J7 学的原封复用）

```text
@AuditLog 注解（RUNTIME + METHOD + value 特权——J7 三件套）
        ↓ 被谁读？
AuditAspect 切面（@Aspect + @Around + proceed——"外包工"）
        ↓ 交给谁写？
AuditLogService（真正写库——切面管"时机"、服务管"记录"）
```

### 5.3 ★ 三个"专业动作"（代码里的关键决策）

| 动作 | 一句话 |
|------|--------|
| **`@Order(1)`** | 审计代理包在**事务代理外层**——记录"最终结局"（提交后的成功 / 回滚后的失败） |
| **`REQUIRES_NEW`** | 审计用**独立新事务**——业务回滚**带不走**失败记录（"失败也要留痕"的底层保障） |
| **`throw exception` 重抛** | 审计**绝不吞业务异常**——否则状态机/值班室/调用方全部失灵 |

## 6. 编码（6 新 + 3 改）

| 文件 | 角色 |
|------|------|
| `pom.xml` | + `spring-boot-starter-aop` |
| `schema-dev.sql` | + 第 9 张表 `audit_log` + 2 索引 |
| `shared/annotation/AuditLog.java`【新】 | ★ **申请书**（自定义注解） |
| `infrastructure/aop/AuditAspect.java`【新】 | ★ **外包工**（@Around + proceed + 失败重抛 + 抓参） |
| `application/service/AuditLogService.java`【新】 | 写库（REQUIRES_NEW + truncate + 日志观测窗） |
| `AuditLogEntity / AuditResult / AuditLogRepository`【新】 | 数据三件套（append-only） |
| 两个 Service | **5 个操作贴申请书**：CREATE_TASK / CONFIRM_PLAN / PAUSE_TASK / RESUME_TASK / CANCEL_TASK |

## 7. 验证记录（2026-10-11，五幕全绿）

### 7.1 HTTP 侧

| 幕 | 请求 | 结果 |
|----|------|------|
| ① 创建 | POST /api/tasks（文件法） | **201** + id `3aa23f89…` |
| ② 暂停 | POST /{id}/pause | **204** |
| ③ 恢复 | POST /{id}/resume | **204** |
| ④ 取消 | POST /{id}/cancel | **204** |
| ⑤ **失败路径** | 对已取消任务再 pause | **400** `INVALID_STATE`"任务已结束（CANCELLED），无法暂停" |

### 7.2 ★ 审计日志（5 行——完整证据链）

```text
12:41:34.654  [审计] CREATE_TASK  target=null         SUCCESS   ← 创建时无 id ✓ 预期
12:43:32.868  [审计] PAUSE_TASK   target=3aa23f89…    SUCCESS
12:43:32.980  [审计] RESUME_TASK  target=3aa23f89…    SUCCESS
12:43:33.071  [审计] CANCEL_TASK  target=3aa23f89…    SUCCESS
12:44:33.046  [审计] PAUSE_TASK   target=3aa23f89…    FAILED    ← ★ 全场最高光
```

**`FAILED` 那一行同时兑现五个知识点**：

1. **失败也留痕**（审计硬要求）；
2. **`REQUIRES_NEW` 在干活**——业务事务回滚了，审计**照旧落库**；
3. **异常重抛生效**——调用方收到 400（异常没被吞）；
4. **`@Around` 的后刀**——catch 分支真被触发；
5. **`@Order(1)` 的位置**——记录的是"回滚后的最终结局"。

**其他细节**：`target=3aa23f89…` 是切面**从方法参数自动抓的 UUID**（`firstUuidArg`）；验证过程中的"日志行重复/丢失"是**终端跨屏复制事故**（查证后归位——RESUME 行确认存在）。

### 7.3 验证工具注记（PowerShell 引号坑的第二次回声）

本场景"创建"那一发**再次撞上 PowerShell 传参坑**：命令行的 JSON（哪怕纯英文、只要含**空格**）单引号版仍 400（`MALFORMED_REQUEST` + `fields:[]`）——根因不是中文，而是 PowerShell 向原生程序传参时的"**引号重组（re-quoting）**"在复杂内容下不可靠（单引号只防住第一层解析，防不住第二层命令行拼接）。

**解法沿用场景④ 的黄金通道——文件法**（`[System.IO.File]::WriteAllText` UTF-8 无 BOM + `-d "@文件"`），一发 **201**。

> **已固化为习惯**：命令行 body **一律文件法**；长期建议接口调试改用 **IDEA HTTP Client / Postman**（无 body 的请求——pause/resume/cancel 这类——命令行依然顺手）。细节见场景④ §6.4"PowerShell 三层坑"。

## 8. ★ 固化：审计规范（本场景产出）

1. **留痕 = 贴注解**：需要审计的方法贴 `@AuditLog("动词_名词")`——**注解即声明**（"这个操作审计了吗"一眼可见）；
2. **失败也要记**（`result=FAILED` + `error_message`）；
3. **审计独立事务**（`REQUIRES_NEW`）——业务回滚带不走；
4. **切面永不吞异常**（记录完**必须重抛**）；
5. **切面在事务外层**（`@Order(1)`——记录最终结局）；
6. **扩展方向**：actor 接用户系统 → 查询接口/审计界面 → 存储可换（MQ/文件——只动 AuditLogService）。

## 9. 复盘与关联地图

| 今天的部件 | 哪课学过 |
|-----------|---------|
| `@AuditLog` 自定义注解（RUNTIME + METHOD） | J7（注释三件套） |
| 反射读标签 + 参数绑定 | J7（getAnnotation 的声明式升级） |
| **代理（CGLIB 戏服）** | **J2**（`$$SpringCGLIB$$0`） |
| `@Around` / `proceed()` 放行 | 本场景（切面语法） |
| `REQUIRES_NEW` 独立事务 | 本场景（事务传播第一课——后续场景展开） |
| 枚举 `AuditResult` / `@Enumerated(STRING)` | J6 |
| 状态机拒绝（⑤ 的 400） | 场景②（守卫） |

**场景在"并发/事务防线家族"里的位置**：

```text
② 乐观锁 = 防并发修改覆盖    ④ 幂等键 = 防重复创建
⑤ 审计   = 让所有操作"有迹可循"（横切层）
```

## 10. ★ 补充：思考题与答案 + 课堂问答收录（2026-10-11）

### 思考题 1（说服题）：说服"复制粘贴派"的两个最有力理由？

**答**：① **"第 6 个操作会来"**——维护成本 = 操作数 × 变更次数（**乘法**）；AOP 加新操作 = 贴一行注解。② ★ **静默缺失的可怕**——复制派漏写是无声的，"**以为有审计**"比"没有审计"更危险（排查到一半才发现盲区）；AOP 下"注解即声明"——**审计覆盖度本身可审计**（扫一眼注解就知道谁没被管）。

### 思考题 2（原理题）：为什么"加注解 + 写切面"就能零改动被插一刀？

**答**：三问三答——① **你调的是谁**：不是原版，是 **Spring 造的代理（替身）**（类文件一行没动——"零改动的秘密不在改代码，在**换演员**"）；② **谁在转发**：替身的方法体（`begin → super.xxx() 转发真身 → success/failure 后刀`）；③ **`proceed()` 放行什么**：**这次方法调用本身**（安检口"抬手放行"）——★ 且**不调用 proceed = 业务方法根本不执行**（拦截的完整权力）。

### 追问 1：`@Around("@annotation(auditLog)")` 和 J7 的反射"不一样"？

**答**：**两种读标签的姿势**——J7 = **手动挡**（自己 for+if+`isAnnotationPresent` 主动翻档案）；场景⑤ = **声明挡**（写一条"筛选规则"，Spring 按规则**自动**匹配——本质仍是反射读标签）。三层：① **为什么是字符串**——切点表达式是 AspectJ 的**迷你 DSL**（要表达"某包/某签名/某注解…"远超"传个类"的表达力）；② ★ **CSS 选择器类比**：`@annotation(auditLog)` ≈ `.some-class`——"凡带此特征者皆被处理"（声明式匹配）；③ ★ **参数绑定魔法**：括号里的 `auditLog` **是变量名声明**——框架把"被拦方法头顶的注解实例"塞给**同名参数**（= `getAnnotation` 的自动版；名字不一致会报 `formal unbound in pointcut`）。

### 追问 2：AspectJ 切点语法全景（"自动挡有哪些方法"）

**答**（速查表）：

| 选人方式 | 写法 | 用途 |
|---------|------|------|
| **execution** | `execution([修饰符] 返回类型 [类.]方法(参数) [throws])` | ★ 最万能（通配符 `*`=一个词 / `..`=任意多 / `+`=本类及子类） |
| **@annotation** | `@annotation(auditLog)` | ★ 按**方法**注解（最精准——本项目） |
| @within / @target | `@within(Foo)` | 按**类**注解 |
| within | `within(com.x.service.*)` | 按**位置** |
| args | `args(UUID, ..)` | 按**实参类型** |
| bean | `bean(*Service)` | ★ Spring 特有（按 bean 名） |

**组合**：`&& || !`；**复用**：`@Pointcut` 命名切点（空方法体作"名字"）。

**通知五件套（刀法）**：`@Before`（前）/ `@After`（finally）/ `@AfterReturning`（成功后）/ `@AfterThrowing`（异常后）/ **`@Around`（环绕——万能）**。

**三个边界坑**：① Spring AOP 只支持**方法执行**连接点（不支持字段/构造器）；② 只拦**容器里的 bean**（自己 `new` 的不拦）；③ ★ **自调用失效**（同类内部 `this.foo()` 不走代理——切面静默失效；**`@Transactional` 有同款经典坑**——未来必撞，先立路标）。

### 追问 3：`proceed()` 是只有 `@Around` 有吗？

**答**：**只有 `@Around` 有**——`proceed()` 是 `ProceedingJoinPoint` 的方法，其余四种拿的是普通 `JoinPoint`（接口里没有 proceed）。原理：**@Around = "拦截者"**（方法还没跑、在等你——必须亲手"放行"）；**其余四种 = "播报员"**（@Before 说完框架自动继续——**它拦不住方法**；@After 家族在跑完后——"都跑完了，还放行什么"）。★ 两种"拦"：`@Around` 不 proceed = **静默拦截**（控制）；`@Before` 抛异常 = **炸桥**（事故）。另：Around 独有兄弟 `proceed(args)`——**改参数放行**。

---

> **下一场景预告：⑥ 异步执行升级——线程池 → Kafka**：把"任务消息化"（J8 学的线程池进阶为真正的消息队列——任务提交与执行彻底解耦，衔接 G1 微服务课）。
