# SP4 · 事务传播与失效场景

> **课程**：Spring 基础实战 · 模块一（2026-10-21）
> **已会**：场景② 的 `@Transactional`（任务状态 + 步骤 + 事件要么全成要么全回滚）、场景④ 的 `BEGIN / ROLLBACK`
> **本次**：**七种传播行为**（重点 REQUIRED vs REQUIRES_NEW 实测对比）、**事务失效四大坑**、**长事务**（场景⑧ 的 LLM 调用就埋在事务里）
> **对标**：廖雪峰 22.3「访问数据库」（事务部分）
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **场景② 事务**（[②任务状态机](../业务场景实战/②任务状态机.md)）：`@Transactional` 方法 = 开启事务 → 执行 → 正常返回提交 / 抛异常回滚；保证"任务状态 + 步骤 + 事件"三张表一致；
- **场景④ 手动事务**（[S4·写数据与事务](../SQL基础实战/S4·写数据与事务.md)）：`BEGIN` / `COMMIT` / `ROLLBACK`；忘写 WHERE 的血案；
- **SP2 代理**：`@Transactional` 是 AOP 增强——**只有经过代理的调用才有事务**（自调用失效同源）。

---

## 一、七种传播行为：一张表 + 一个比喻

**传播行为（Propagation）回答一个问题：一个"已经在事务里"的方法，调用另一个标了 `@Transactional` 的方法时，后者怎么办？**

| 传播行为 | 行为 | 前端类比 |
|---|---|---|
| **REQUIRED**（默认） | 有事务就加入；没有就新建 | **拼车**：有车就上，没车自己开一辆 |
| **REQUIRES_NEW** | 总是新建独立事务，挂起外层 | **专车**：自己单开一辆，别人的事故不牵连你 |
| **NESTED** | 在外层事务内开子事务（savepoint） | **存档点**：回滚到存档点，不影响之前进度 |
| SUPPORTS | 有就加入，没有就非事务跑 | 搭顺风车 |
| NOT_SUPPORTED | 挂起外层，非事务跑 | 下车步行 |
| MANDATORY | 必须已有事务，否则报错 | 没车就不上车（直接拒绝） |
| NEVER | 必须没事务，否则报错 | 绝不坐车 |

**默认 REQUIRED**——所以绝大多数业务代码"看起来有事务"，实际是**共享同一个事务**。
---

## 二、★ 核心实验：REQUIRED vs REQUIRES_NEW（同一段代码，只换传播行为）

### 2.1 实验设计

外层方法（默认 REQUIRED）先插一行，再调用"内层方法"，内层方法**故意抛异常**，外层用 `try-catch` 吞掉后继续插一行：

```text
外层 REQUIRED： INSERT outer → 调用内层（抛异常）→ catch → INSERT after → 提交
内层两种写法：
   A. @Transactional(REQUIRED)      ← 加入外层事务
   B. @Transactional(REQUIRES_NEW)  ← 新建独立事务
```

### 2.2 预期（这是本课最重要的认知差）

| 内层传播行为 | 表里最终有几行 | 为什么 |
|---|---|---|
| **REQUIRED** | **0 行**（连 outer 也没了） | 内层加入外层事务，抛异常后**整个事务被标记 rollback-only**；外层 catch 住异常继续跑，但提交时会抛 `UnexpectedRollbackException`，**全部回滚** |
| **REQUIRES_NEW** | **2 行**（outer + after） | 内层是独立事务，它失败只回滚自己；外层不受影响，正常提交 |

**这解释了生产里最常见的困惑**：`catch` 住了内层异常，为什么外层数据也没了？——因为 **REQUIRED 下它们根本是"同一条命"**。

### 2.3 实测结果（2026-10-21 全绿）

```text
[SP4-A] 外层捕获内层异常（REQUIRED）：inner-required 故意失败
[SP4-A] 外层方法在提交阶段抛出异常：
        UnexpectedRollbackException - Transaction silently rolled back
        because it has been marked as rollback-only
[SP4-A] 内层 REQUIRED：最终行数 = 0（预期 0），内容 = []

[SP4-B] 外层捕获内层异常（REQUIRES_NEW）：inner-new 故意失败
[SP4-B] 内层 REQUIRES_NEW：最终行数 = 2（预期 2），内容 = [outer-new, after-new]
```

**两条铁证**：

1. **REQUIRED 下 `catch` 救不了事务**——异常被捕获、代码继续执行，但事务已被标记 `rollback-only`，提交时抛 `UnexpectedRollbackException`，连外层先插的那行也一起消失了；
2. **REQUIRES_NEW 实现了真正的隔离**——内层独立事务失败只回滚自己，外层 `outer-new` 与 `after-new` 照常落库。

**记忆钩子**：REQUIRED = 同一条命；REQUIRES_NEW = 各安天命。

---

## 三、事务失效四大坑（配 SP2 的代理知识）

| # | 坑 | 现象 | 原因 | 修复 |
|---|---|---|---|---|
| 1 | **自调用** | `this.method()` 上标 `@Transactional` 不生效 | 绕过代理（SP2 已实测） | 拆类 / `@Lazy` 自注入 / `AopContext` |
| 2 | **非 public 方法** | 私有/包级方法上的事务静默失效 | CGLIB 无法正确覆写 | 改 public |
| 3 | **异常被吞** | 捕获后不抛出，事务不回滚 | Spring 靠"异常冒泡到代理"判定回滚 | 要么抛出去，要么 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` |
| 4 | **rollbackFor 默认只回滚 RuntimeException** | 抛 `IOException`（受检异常）事务照样提交 | 默认 `rollbackFor = RuntimeException` | `@Transactional(rollbackFor = Exception.class)` |

**第 4 坑的实证**：`throw new IOException()` 会被提交——因为受检异常不在默认回滚名单里。

### 3.1 补充两个高频失效

- **多线程 / `@Async` 里没事务**：事务绑定在**线程**上（ThreadLocal），子线程没有父线程的事务；
- **事务方法里开新线程 / 异步调用**：同理，新线程不在事务上下文。

---

## 四、★ 长事务：场景⑧ 埋的一颗雷

**事实**：`ResearchPlanningService.generateInitialPlan` 标了 `@Transactional`，而它内部调用的 `llmPlanGenerator.generate()` **会发起一次 4-9 秒的 LLM 网络请求**（场景⑧ 实测 6247 ms）。

**后果**：

```text
事务开始 → 建计划实体（未提交，持有行锁/连接）
        → 调 LLM，等 6 秒（★ 数据库连接一直被占着，事务一直开着）
        → 落步骤 → 提交
```

| 风险 | 说明 |
|---|---|
| **连接池耗尽** | 每个请求占一个连接 6+ 秒，并发一高就排队（HikariCP 默认 10 个连接） |
| **锁等待** | 事务期间持有写锁，其他请求修改同一任务会阻塞 |
| **回滚代价** | 事务开太久，undo/redo 日志膨胀 |

**正确姿势**（也是本次实验之后的改进方向）：

```text
① 先在事务外调 LLM（拿到计划草稿）
② 再开短事务批量落库（计划 + 步骤 + 事件）
③ 或把 LLM 调用挪到 @Transactional 方法之外
```

**判定标准**：**事务里绝不发起网络调用**（RPC / HTTP / MQ / LLM 都算）。

---

## 五、项目关联地图

| 概念 | 项目位置 |
|---|---|
| @Transactional 真实用法 | `ResearchPlanningService.generateInitialPlan`（**同时是长事务反例**） |
| 事务 + 乐观锁 | `ResearchTaskCommandService`（场景②④：version 字段） |
| 手动事务对照 | S4 课程里的 `BEGIN / SET AUTOCOMMIT FALSE` |
| 传播行为实验 | `infrastructure/tx/TransactionDemoService`（SP4 新增，可删） |
| 自调用失效 | SP2 的 `AopDemoService`（同源） |

---

## 六、验证记录（2026-10-21）

| 验证项 | 结果 | 判定 |
|---|---|---|
| 场景 A：内层 REQUIRED | **0 行**；提交阶段抛 `UnexpectedRollbackException`（rollback-only） | ✅ 与预期完全一致 |
| 场景 B：内层 REQUIRES_NEW | **2 行**（`outer-new`、`after-new`），内层失败只回滚自己 | ✅ 与预期完全一致 |
| 实验表 | `sp4_tx_demo`（Runner 自动建表，只存在于 dev 库） | ✅ 无需清理（演示用） |

**附带确认**：`@Lazy` 自注入拿到的确实是**代理**——否则 `self.innerRequired()` 不会生效，两组实验的结果不可能出现差异（这次差异就是事务生效的直接证据）。

---

## 七、思考题（自查 + 参考答案）

1. 外层 REQUIRED 调内层 REQUIRED，内层抛异常、外层 catch 住了——为什么最后全部回滚？
   **参考答案**：内层加入的是同一个事务；它抛异常时，Spring 的事务拦截器把**整个事务标记为 rollback-only**。外层 catch 只是拿到了异常对象，事务标记改不回来；提交时 Spring 发现"标记为只能回滚"→ 抛 `UnexpectedRollbackException` → 全部回滚。**REQUIRED 下内外层是一条命。**
2. 什么场景该用 REQUIRES_NEW？
   **参考答案**：**子操作失败不应拖垮主流程**的场景。典型：写审计日志 / 发通知 / 记录失败补偿信息——主业务回滚了，日志必须留下（否则"为什么失败"都没记录）。场景⑤ 的审计如果和业务同事务，业务回滚时审计也会丢。
3. 事务里调了一次 6 秒的 HTTP 接口，会有什么后果？
   **参考答案**：数据库连接被占用 6 秒（连接池紧张、并发下排队）、写锁持有 6 秒（其他请求阻塞）、长事务导致日志膨胀。**事务内不做网络调用**；把调用挪到事务外，或先取数据再短事务落库。
4. `@Transactional` 标注的私有方法为什么会失效？
   **参考答案**：Spring 事务靠**代理**实现，CGLIB 生成的子类**无法覆写私有方法**（私有方法不参与多态），增强逻辑挂不上去。改 public；或者把事务边界移到调用方。

---

## 八、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| `backend/.../infrastructure/tx/TransactionDemoService.java` | 传播行为对比（REQUIRED / REQUIRES_NEW） | ✅ 可删 |
| `backend/.../infrastructure/tx/TransactionDemoRunner.java` | 启动后自动建表 + 跑两组对比 + 打印结果 | ✅ 可删 |

> 成组删除（Runner 引用 Service）。

---

## 九、本课问答记录（2026-10-21）

（待补——学员提问后按双台账纪律记录）
