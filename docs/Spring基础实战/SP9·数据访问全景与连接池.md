# SP9 · 数据访问全景与连接池

> **课程**：Spring 基础实战 · 模块三（2026-10-21）
> **已会**：SP4 事务（`@Transactional` 与传播行为）、场景③⑤⑦ 的 SQL、H2 库、项目 JPA/MyBatis 双模式
> **本次**：**一次 `save()` 经过了谁**（分层图）、**HikariCP 连接池**参数、**事务与连接的关系**（长事务为什么危险）、**JPA vs MyBatis** 的项目级对照、用 Actuator 观察连接池
> **对标**：廖雪峰 22.3「访问数据库」
> **状态**：✅ 讲义完成

---

## 零、前置知识速查（复习专用）

- **SP4 事务**：`@Transactional` = 一个事务（开启→执行→提交/回滚）；REQUIRED 下内外层共用同一事务；
- **SP4 长事务案例**：`ResearchPlanningService.generateInitialPlan` 在事务里调 LLM（4~9 秒）——本课用连接池视角量化它的代价；
- **项目双持久化**：`TaskEventLogStore` 接口 + `JpaTaskEventLogStore`（JPA）/ `MyBatisTaskEventLogStore`（MyBatis）两个实现，`APP_PERSISTENCE_MODE` 切换，启动时 `PersistenceModeDemoRunner` 打印两套 SQL。

---

## 一、分层图：一次 `repository.save()` 经过了谁

```text
业务代码（Service）
   ↓ 调接口
Repository / Store（JpaTaskEventLogStore / MyBatisTaskEventLogStore）
   ↓ 翻译成 SQL 或对象操作
ORM 层（Hibernate / MyBatis Executor）
   ↓ 需要连接
事务管理器（JpaTransactionManager / DataSourceTransactionManager）
   ↓ 从池里借一个连接
DataSource（HikariCP）—— 连接池
   ↓ 借出的是物理连接
JDBC 驱动（org.h2.Driver / org.postgresql.Driver）
   ↓ TCP
数据库（H2 / PostgreSQL）
```

**每层的职责**：

| 层 | 干什么 | 项目位置 |
|---|---|---|
| Store/Repository | 业务语义的存取方法 | `TaskEventLogStore` + 两个实现 |
| ORM | 对象 ↔ SQL 的翻译 | JPA：`JpaTaskEventLogStore`；MyBatis：`MyBatisTaskEventLogStore` + `*Mapper.xml` |
| 事务管理器 | 事务的开启/提交/回滚，**持有连接** | Spring 按持久化技术自动选（JPA 用 `JpaTransactionManager`） |
| DataSource | 连接的"池"（借出/归还） | HikariCP |
| JDBC 驱动 | 真正与数据库通信 | 依赖里自带 |

**关键认知**：**事务管理器是"持有连接"的那一层**——事务开始借连接，提交/回滚归还。这决定了下一节的全部结论。

---

## 二、DataSource 与 HikariCP

### 2.1 DataSource 是什么

`javax.sql.DataSource` 是 JDBC 规范定义的**连接工厂**接口：`getConnection()` 返回一个连接。Spring Boot 默认实现是 **HikariCP**（性能最好）。

### 2.2 HikariCP 默认参数（项目未显式配置，用的就是默认值）

| 参数 | 默认值 | 含义 |
|---|---|---|
| `maximumPoolSize` | **10** | 池里最多 10 个连接 |
| `minimumIdle` | 同 maximumPoolSize | 最少保持的空闲连接 |
| `connectionTimeout` | 30s | 借连接最多等 30 秒（超时抛异常） |
| `idleTimeout` | 10min | 空闲连接多久被回收 |
| `maxLifetime` | 30min | 连接最长寿命（防数据库侧断连） |
| `poolName` | `HikariPool-1` | 日志里看到的池名字 |

配置写法：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 3000   # 生产常调小，快速失败
```

**为什么默认 10**：连接是数据库的稀缺资源，**不是越多越好**——10 个连接足够支撑几十 QPS 的常规业务；调到 100 反而可能把数据库打挂（每个连接都占数据库内存/线程）。

### 2.3 池的工作方式

```text
应用启动：预建 minimumIdle 个连接
请求要连接：从池里借（空闲的直接给，没有就新建，到上限就排队）
用完归还：连接回池，不关闭（关闭物理连接代价高）
```

---

## 三、★ 事务与连接：一个事务 = 一个连接

### 3.1 生命周期

```text
@Transactional 方法开始
   → 事务管理器向 DataSource 借一个连接
   → 方法执行期间，这个连接【被独占】（其他事务借不到）
   → 提交/回滚
   → 连接归还池子
```

**所以**：**事务持续多久，连接就被占用多久**。

### 3.2 长事务的真实代价（场景⑧ 的案例量化）

`ResearchPlanningService.generateInitialPlan` 是 `@Transactional`，里面调 LLM（实测 6.2 秒）：

```text
事务开始 → 借连接 → 建计划实体 → 【调 LLM 等 6 秒，连接被占着】 → 落步骤 → 提交 → 归还
```

| 并发数 | 占用连接 | 池容量 10 时的后果 |
|---|---|---|
| 1 | 1 | 无感 |
| 5 | 5 | 池子半满 |
| **10** | **10** | **池子打满，第 11 个请求开始排队**（等 connectionTimeout 30 秒后报错） |

**结论**：**事务里绝不发起网络调用**（LLM / RPC / HTTP / MQ）——把调用挪到事务外，事务只包"落库"。

### 3.3 连接泄漏

框架会自动归还（`try-finally`），但两种写法会"占着不还"：

1. **事务方法里开了异步线程**：连接绑定在线程上，子线程拿不到父线程的事务；
2. **手写 JDBC 忘关**：`Connection` 不 close（用 `try-with-resources` 兜底）。

---

## 四、JPA vs MyBatis（项目双实现对照）

| 维度 | JPA / Hibernate | MyBatis |
|---|---|---|
| 设计哲学 | 面向对象，操作实体 | SQL 优先，手写 SQL |
| SQL 来源 | 框架自动生成（可覆盖） | 你写在 `*Mapper.xml` 里 |
| 简单 CRUD | 快（`save/findById` 一行） | 要写 SQL + 映射 |
| 复杂查询 | 别扭（Criteria / JPQL，复杂报表难写） | 灵活（任意 SQL） |
| SQL 可控性 | 低（生成什么靠猜/看日志） | 高（所见即所得） |
| 学习曲线 | 高（实体状态/懒加载/缓存） | 低（会 SQL 就会） |
| 典型场景 | 领域模型复杂、CRUD 为主 | 报表/复杂查询、DBA 要求 SQL 可审 |

**项目做法（最佳实践）**：**同一接口，两套实现，配置切换**：

```text
TaskEventLogStore（接口）
   ├── JpaTaskEventLogStore        @ConditionalOnProperty(mode=jpa)
   └── MyBatisTaskEventLogStore    @ConditionalOnProperty(mode=mybatis)
```

`PersistenceModeDemoRunner`（@Profile("dev")）启动时写入 + 查询一条演示事件，**两套实现的 SQL 都打印在日志里**，可直接对照。

### 4.1 ★ 同一操作的 SQL 对照（2026-10-23 学员要求补充）

> **JPA 不减少 SQL，它只是把 SQL 藏起来**——学它必须先看它替你写了什么。

| 操作 | JPA 写法 | **底层生成的 SQL** | 直接 SQL 写法 |
|---|---|---|---|
| 按主键查 | `findById(id)` | `SELECT * FROM research_task WHERE id = ?` | `jdbcTemplate.queryForObject("SELECT * FROM research_task WHERE id = ?", ...)` |
| 分页查 | `findAll(PageRequest.of(0, 10))` | `SELECT * FROM research_task LIMIT ? OFFSET ?` + `SELECT COUNT(*) FROM research_task` | 同左两条 |
| 派生查询 | `findByTaskIdOrderByCreatedAtDesc(taskId)` | `SELECT * FROM task_event_log WHERE task_id = ? ORDER BY created_at DESC` | 手写同一条 |
| 批量 IN | `findByTaskIdIn(ids)` | `SELECT * FROM research_step WHERE task_id IN (?, ?, ...)` | 同左 |
| 新增 | `save(entity)` | 无 id：`INSERT INTO research_task (...) VALUES (...)`<br>有 id：`UPDATE ... WHERE id = ?` | `INSERT` / `UPDATE` |
| 删除 | `deleteById(id)` | `DELETE FROM research_task WHERE id = ?` | `DELETE` |
| 关联懒加载 | `task.getSteps()` | **每个任务一条**：`SELECT * FROM research_step WHERE task_id = ?` | 一次 JOIN 或一次 IN |

**派生查询命名规则（能预判 SQL 的关键）**：

```text
findBy      前缀        → SELECT
TaskId      字段名      → WHERE task_id = ?
OrderBy     排序        → ORDER BY
CreatedAt   字段名      → created_at
Desc        方向        → DESC
```

**项目实证（启动日志里的两套 SQL）**：

```text
JPA 模式：   Hibernate: insert into task_event_log (...) values (...)
MyBatis 模式：==>  Preparing: INSERT INTO task_event_log (...) VALUES (?, ..., CAST(? AS JSON), ...)
```

**同一条插入，JPA 由框架生成、MyBatis 由你手写**——输出几乎一样，区别只在"谁写的"。**看懂 SQL 再回看框架，才不会"只会用不会修"**。

---

## 五、用 Actuator 观察连接池

对应 SP7 的 metrics 端点：

```powershell
GET /actuator/metrics/hikaricp.connections.active    # 正在被借出的连接数（事务占用中）
GET /actuator/metrics/hikaricp.connections.idle      # 空闲连接数
GET /actuator/metrics/hikaricp.connections.max       # 池上限（10）
GET /actuator/metrics/hikaricp.connections.min       # 最小空闲
GET /actuator/metrics/hikaricp.connections.pending   # 排队等连接的请求数（>0 = 池打满）
```

**判读**：`active` 接近 `max` + `pending` 持续 > 0 → 池子不够用或存在长事务/泄漏。
---

## 六、实验：三组验证（2026-10-21 全绿）

### 6.1 连接池指标（空闲态）

```text
hikaricp.connections.active  = 0.0
hikaricp.connections.idle    = 10.0
hikaricp.connections.max     = 10.0
hikaricp.connections.min     = 10.0
hikaricp.connections.pending = 0.0
```

→ 印证 HikariCP **默认 maximumPoolSize = 10**（项目未显式配置）。

### 6.2 ★ 长事务占连接（模拟 SLOW + 后台 POST）

```text
【事务进行中】active=1.0  idle=9.0   pending=0.0     ← 事务借走一个连接
【事务结束后】active=0.0  idle=10.0                   ← 提交后归还
```

**这一组是本节最硬的证据**：`generateInitialPlan` 在事务里等 LLM（模拟 SLOW 下约 9 秒），期间 `active` 稳定为 1、`idle` 掉到 9——**一个事务 = 一个连接，事务持续多久，连接就被占多久**。

**推论**：10 个并发这样的事务 → `active=10`、池满 → 第 11 个请求排队直到 `connectionTimeout`（30 秒）超时。

### 6.3 JPA vs MyBatis SQL 对照（MyBatis 模式日志）

```text
当前持久化实现：MyBatisTaskEventLogStore$$SpringCGLIB$$0
==>  Preparing: INSERT INTO task_event_log (id, task_id, step_id, event_type, event_message,
                event_payload, operator_type, operator_id, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?)
==>  Preparing: SELECT id, task_id, ... FROM task_event_log WHERE task_id = ? ORDER BY created_at DESC
```

**两个观察**：

1. SQL 是**手写在 Mapper 里的**（可见 `CAST(? AS JSON)` 这种细节——完全可控）；
2. `$$SpringCGLIB$$0` → **Store 也被 CGLIB 代理**（它标了 `@Transactional`）——SP2 的代理知识在这里再次出现。

JPA 模式的对照：日志是 `Hibernate: insert into task_event_log (...)`（框架生成，无 `==>  Preparing:` 前缀）。

---

## 七、项目关联地图

| 概念 | 项目位置 |
|---|---|
| Store 接口 + 双实现 | `TaskEventLogStore` / `JpaTaskEventLogStore` / `MyBatisTaskEventLogStore` |
| 持久化切换 | `APP_PERSISTENCE_MODE`（jpa / mybatis） |
| 双模式演示 | `PersistenceModeDemoRunner`（@Profile("dev")，启动时打印两套 SQL） |
| MyBatis SQL | `TaskEventLogMapper.xml` / `ResearchTaskMapper.xml` |
| 连接池 | HikariCP（默认 10，项目未显式配置） |
| 连接池指标 | `/actuator/metrics/hikaricp.connections.*` |
| 长事务反例 | `ResearchPlanningService.generateInitialPlan`（事务内调 LLM） |

---

## 八、思考题（自查 + 参考答案）

1. 为什么连接池不是越大越好？<br>
   **参考答案**：连接是**数据库的稀缺资源**——每个连接占数据库侧的内存/线程；池子调大只是让应用"能同时要更多连接"，数据库侧压力同步上升。默认 10 足够常规业务；过大的池还会增加连接创建/切换开销。**判据是数据库能否承受，不是应用想开多少**。
2. 事务方法里调了一次 6 秒的 LLM，10 个并发请求会怎样？<br>
   **参考答案**：每个请求的事务各占一个连接，10 个并发 → `active=10`、池打满；第 11 个请求开始排队，直到 `connectionTimeout`（30 秒）抛异常。这就是把 LLM 调用放在事务内的代价——**功能没坏，但吞吐被连接池卡死**。
3. JPA 和 MyBatis 怎么选？<br>
   **参考答案**：CRUD 为主、领域模型复杂 → JPA（少写 SQL）；报表/复杂查询、SQL 需可审可调 → MyBatis（完全可控）。项目用"**接口 + 双实现 + 配置切换**"——两者都要，按场景选。
4. `hikaricp.connections.pending` 持续 > 0 说明什么？<br>
   **参考答案**：所有连接都被借出，请求在排队等连接。原因通常是：① 池太小；② **长事务**（连接被占太久）；③ 连接泄漏。排查顺序：先看有无长事务（慢 SQL / 事务内网络调用），再考虑调池。

---

## 九、本课实验文件清单

| 文件 | 作用 | 是否可删 |
|---|---|---|
| **无新增 Java 代码** | 本课是"观察课"：用 Actuator 指标 + 现有双持久化实现做验证 | — |

> 与 SP7 一样，本课的价值在"**看出**"而不是"写出"——连接池的代价只有观察指标才能看见。

---

## 十、本课问答记录（2026-10-21）

| # | 提问（原话） | 落档位置 |
|---|---|---|
| **Q1** | "HikariCP 这个是什么意思" | 本文 §2.1 补充 + 本节 Q1 详解（见下） |
| **Q2** | "涉及 SQL 的不要以 JPA 的写法讲解……JPA 封装太深了，我不能很好学习到实现的原理" | §4.1「同一操作的 SQL 对照」——JPA 每种写法旁标"底层生成的 SQL"；并固化为**全部课程**的写作约定（Spring 总纲约定第 9 条） |

### 10.1 Q1 详解：HikariCP 是什么

**名字**：`Hikari` = 日语「光」（寓意"快如光"）；`CP` = Connection Pool（连接池）——**一个 JDBC 连接池实现**。

**连接池解决什么**：数据库连接很贵（TCP 握手 + 认证 + 权限加载，几十毫秒）；每个请求都新建连接会拖慢响应并压垮数据库。连接池在启动时预建一批连接，**借出/归还/复用**。

**它在分层中的位置**：

```text
应用代码 → JPA/MyBatis → 事务管理器 → DataSource（接口）→ HikariCP（实现）→ JDBC → 数据库
```

`DataSource` 是 JDBC 规范接口（`javax.sql.DataSource`），HikariCP 是其实现；Spring Boot 自动配置发现 classpath 有 HikariCP → 自动用它创建 DataSource。

**为什么默认是它**：Spring Boot 2.0+ 默认连接池（此前是 Tomcat JDBC）；性能最好（无锁设计 `ConcurrentBag`）、代码量小。同类还有 Druid（监控/SQL 防火墙）、Tomcat JDBC、C3P0（已少用）。

**项目证据**：启动日志 `HikariPool-1 - Starting... → Start completed`；指标 `hikaricp.connections.*`（§六 实测 max=10）；项目未显式配置，全用默认值。

**前端类比**：axios 的 keep-alive（复用 TCP 连接）/ 共享单车（不每次买车）——昂贵资源的复用池。

**调参位置**：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```
