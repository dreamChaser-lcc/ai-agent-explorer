# 专题 · 分层命名体系（PO / DO / DTO / VO / DAO / BO / POJO）

> **课程**：Java 基础实战 · 补充专题（2026-10-27 课堂问答）
> **起因**：学员提问"DTO DAO PO VO 等一些名称搞不清"
> **目标**：搞懂每一层的"为什么存在"，看懂任何公司的命名体系
> **状态**：✅ 完成（含本项目 29 个类的逐一对照）

---

## 一、先分两大类：装数据的 vs 干活的（最容易混的地方）

```text
【数据载体】装数据的对象（名词）—— 它们是"行李"
  PO / Entity / DO / DTO / VO / BO / POJO

【操作组件】干活的类（动词）—— 它们是"搬运工"
  DAO / Repository / Mapper / Service / Controller
```

★ **最常见的误解就是把 DAO 和 PO/VO 并排当成一类东西**。
DAO 不是"数据对象"——它是【访问数据的组件】。PO/DTO/VO 才是数据本身。

---

## 二、全量清单

| 缩写 | 全称 | 中文 | 一句话定义 | 所在层 |
|---|---|---|---|---|
| **PO** | Persistent Object | 持久化对象 | 数据库表的一行的映射 | 数据层 |
| **Entity** | 实体 | — | 同 PO（JPA 的官方叫法） | 数据层 |
| **DO** | Domain Object | 领域对象 | 业务领域里的对象（DDD 风格） | 领域层 |
| **DTO** | Data Transfer Object | 数据传输对象 | 在层与层之间搬运数据的容器 | 层间传输 |
| **VO** | View Object | 视图对象 | 专门为某个前端页面定制的展示结构 | Web 层 |
| **BO** | Business Object | 业务对象 | 封装了业务行为（方法）的业务对象 | 业务层 |
| **POJO** | Plain Old Java Object | 简单 Java 对象 | 不依赖框架的普通类（以上全部都是 POJO） | 任意（统称） |
| **DAO** | Data Access Object | 数据访问对象 | **封装数据库增删改查的组件** | 数据层 |
| **Query / Param** | — | 查询参数 | 封装查询条件 | Web → 业务 |
| **Request / Command** | — | 请求 | 前端 → 后端的输入 | Web 层 |
| **Response / Result** | — | 响应 | 后端 → 前端的输出 | Web 层 |
| **Mapper** | — | 转换器 | 对象之间字段拷贝/转换（或 MyBatis 的 DAO） | 层间 |

> **Mapper 的双重身份**：在"对象转换"语境里指转换器（如 MapStruct）；在 MyBatis 语境里指 DAO 接口。看上下文。

---

## 三、逐个详解

### 1. PO / Entity（持久化对象）

```text
和数据库表【一一对应】：一个 Entity ↔ 一张表的一个行
JPA 用 @Entity 标注
本项目：ResearchTaskEntity ↔ 表 research_task
```

**特征**：只有字段 + getter/setter（或 record），不含业务方法；带 JPA 注解（`@Id`/`@Column`/`@Version`）。

### 2. DO（领域对象）

```text
DDD（领域驱动设计）的叫法。
PO 是"表的映射"，DO 是"业务概念"——两者在简单项目里合二为一，
在复杂 DDD 项目里分开：DO 带业务方法，PO 只负责存储。
```

### 3. DTO（数据传输对象）

```text
目的：在层与层之间搬运数据，【不暴露内部结构】。
范围很广：请求参数、响应结果、跨服务调用的载荷，都算 DTO。
它是"统称"而非特指——本项目用 Request/Response 后缀实现 DTO 思想。
```

### 4. VO（视图对象）

```text
专门为某个页面/视图定制的展示结构。
与 DTO 的关系：语义有细微差别（VO 面向"显示"），实践中大量混用，
知道区别即可，不必较真。
```

### 5. BO（业务对象）

```text
封装业务行为的对象——不只是数据，还有方法。
例：OrderBO.calculateTotalPrice()。
简单项目里很少单独出现（业务逻辑直接写在 Service）。
```

### 6. POJO（统称）

```text
Plain Old Java Object：不继承框架类、不实现框架接口的普通 Java 类。
PO / DTO / VO / BO / Entity 全部都是 POJO 的子集。
这个词的意义在于"反 EJB 时代的重量级组件"——强调简单。
```

### 7. DAO（数据访问对象）★ 不是数据，是组件！

```text
封装对数据库的增删改查的类/接口。
本项目：RetentionCleanupDao（deleteChildrenByTaskIds / deleteOldEvents ...）
```

**名称演化史（三个名字，同一个职责）**：

```text
DAO（传统 EJB 时代）→ Repository（DDD / Spring Data）→ Mapper（MyBatis）
职责不变：把"怎么访问数据库"封装起来，让上层只管调方法。
```

---

## 四、本项目的对应关系（29 个类逐一对照）

| 项目类 | 概念 | 说明 |
|---|---|---|
| `ResearchTaskEntity` 等 12 个 | **PO / Entity** | `@Entity`，一一映射数据库表 |
| `CreateResearchTaskRequest` | **DTO（请求）** | 前端 → 后端 |
| `PlanConfirmationRequest` | **DTO（请求）** | 前端 → 后端 |
| `TaskSummaryResponse` / `TaskDetailResponse` 等 6 个 | **VO / DTO（响应）** | 后端 → 前端 |
| `PageResponse<T>` | **DTO（分页壳）** | 通用分页结构 |
| `ApiError` | **DTO（错误响应）** | 统一错误体 |
| `ResearchTaskRepository` 等 11 个接口 | **DAO**（Spring Data 形态） | 接口，框架自动实现 |
| `RetentionCleanupDao` | **DAO**（手写，JdbcTemplate） | 显式命名 Dao |
| `TaskResponseMapper` | **转换器**（Entity → VO） | 层间翻译 |

**本项目的命名风格（现代简化派）**：

```text
数据层：XxxEntity        （不用 PO 后缀）
请求：  XxxRequest       （不用 DTO 后缀）
响应：  XxxResponse      （不用 VO 后缀）
访问：  XxxRepository（Spring Data）+ XxxDao（手写 JDBC）
```

---

## 五、一个请求中，对象怎么流转（完整链路）

```text
前端 POST {"title":"调研","goal":"...","executionMode":"ASYNC"}
  ↓ ① JSON 反序列化
CreateResearchTaskRequest                     ← DTO（请求）
  ↓ ② Service 业务处理
ResearchTaskEntity entity = new ...(title, goal)   ← PO/Entity
  ↓ ③ repository.save(entity)                ← DAO
数据库 INSERT
  ↓ ④ 查询
ResearchTaskEntity saved                      ← PO
  ↓ ⑤ TaskResponseMapper 转换
TaskSummaryResponse                           ← VO/DTO（响应）
  ↓ ⑥ JSON 序列化
前端 {"id":"f84b...","taskNo":"TASK-123",...}
```

**两次"变身"的原因**：

| 转换 | 为什么 |
|---|---|
| ① 不直接收 Entity | 防止前端注入 `id`/`createdAt`/`version` 等**服务端才该控制的字段** |
| ⑤ 不直接回 Entity | ① 防懒加载序列化爆炸（`LazyInitializationException` / N+1）② 防敏感字段泄露 ③ 防表结构变化直接冲击前端 |

---

## 六、为什么不让"一个类走天下"（三个真实事故场景）

1. **安全事故**：Entity 直接序列化返回 → `@Version` 字段、内部状态、甚至 `password` 字段全暴露给前端。
2. **序列化爆炸**：Entity 带 `@OneToMany` 懒加载 → JSON 序列化时触发级联查询（N+1）或直接抛 `LazyInitializationException`（Session 已关闭）。
3. **耦合事故**：表加了一列 `internal_flag` → 前端接口响应也多了这个字段 → 全部页面要 review。

**一句话**：**层与层之间的墙，就是靠"不同对象 + 转换"砌起来的。**

---

## 七、命名流派（企业现实）

| 流派 | 命名体系 | 典型场景 |
|---|---|---|
| 传统教科书 | PO / VO / DAO / BO | 老项目、教材 |
| 阿里规范 | DO / DTO / VO / Query | 国内互联网 |
| DDD | Entity / ValueObject / Aggregate / Repository | 复杂业务域 |
| 现代简化 | Entity / Request / Response | 新项目（**本项目**） |

**关键认知**：**名字是皮，分层是骨**。换一家公司名字全变，但"为什么分层"的答案永远一样。

---

## 八、三个误区

1. **"DAO 是一种数据对象"**
   ❌ DAO 是访问组件（类），PO/DTO/VO 才是数据载体。它俩根本不是一类东西。

2. **"VO 和 DTO 就是同一个东西的不同叫法"**
   语义有细微差别（VO 面向视图展示，DTO 面向传输），实践中大量混用。知道即可。

3. **"字段都一样，何必转来转去"**
   见第六节三个事故场景。今天字段一样，明天就分化了——转换层就是防分化扩散的堤坝。

---

## 九、一句话总结

```text
装数据的：PO/Entity（映射表）→ DO（业务概念）→ DTO/VO（传输/展示）—— 按层变身
干活的：  DAO / Repository / Mapper（访问数据库）—— 负责搬运
命名的：  公司不同叫法不同；看懂比背熟重要，分层思想比名字重要
```

---

## 十、课堂问答记录（2026-10-27）

（待补——学员提问后按双台账纪律记录）

---

## 九、面试问法与复习入口（2026-10-27 补）

**面试三连**：

```text
Q1：PO / DO / DTO / VO 区别？
    → 两大类先分清：装数据的（行李）vs 干活的（搬运工）
    → 再按层说：PO 映射表 / DO 业务概念 / DTO 层间传输 / VO 面向展示（§二清单）

Q2：DAO 是一种数据对象吗？
    → 不是！DAO 是【访问组件】（干活的），PO/DTO/VO 才是数据本身
    → 最常见误区就是把它俩并排（§一）

Q3：为什么不让一个类走天下（Entity 直接收发）？
    → 三个事故场景：安全泄露 / 懒加载序列化爆炸 / 表结构耦合前端（§六）
```

**复习入口（考前 10 分钟）**：

```text
§四 项目 29 个类的逐一对照表（Entity 12 / Request 2 / Response 6 /
   Repository 11 + Dao 1 / Mapper 1）
§五 一个请求的六步对象流转（DTO→PO→DAO→PO→VO→JSON）
```
