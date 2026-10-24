# 技术栈对照：research-agent vs live-java 规范

> **来源**：`https://git-vz.flycat.tech/live-java/project-specification/src/branch/main/live-java/specification.md`（作者 zengbin，2026-09-21）
> **核对日期**：2026-10-24
> **结论**：差异不是"少几个组件"，而是**两套不同时代的架构**——不建议硬对齐，只做低成本项补齐。
> **对应的学习计划**：[课程总纲](00·课程总纲.md)（15 个专题，四期，含验收题）

---

## 一、版本基线（红线级）

| 项 | 本项目 | 规范 | 影响 |
|---|---|---|---|
| **Java** | **21** | **11（唯一明确版本）** | Boot 3 要求 Java 17+ |
| **Spring Boot** | **3.5.13** | 未标注（Java 11 → 只能 2.x） | `jakarta.*` vs `javax.*`，规范代码无法直接复用 |
| 构建 | Maven | 未涉及 | — |
| 测试 | JUnit 5 + Mockito + AssertJ | 未涉及（仅"测试通过"） | 本项目更完整 |

**要"对齐"就意味着降级技术栈**——这是不可接受的倒退。

---

## 二、架构形态

| 项 | 本项目 | 规范 |
|---|---|---|
| 形态 | **单体** | **Spring Cloud 微服务** |
| 注册/配置 | nacos 依赖存在但**禁用** | **Nacos 必备** |
| 服务调用 | 无 | **OpenFeign** |
| 网关 | 无 | 统一网关 + 路径前缀 `/v1/{端}/{模块}` |

---

## 三、持久层

| 项 | 本项目 | 规范 | 冲突 |
|---|---|---|---|
| ORM | **JPA/Hibernate** + 原生 MyBatis | **MyBatis-Plus** | 重写 Repository |
| 数据源 | **1 个** | **15 个**（`@DS`） | 架构级 |
| **事务** | **`@Transactional` 22 处** | **禁止业务方法加事务**（`@DS` 失效） | ⛔ 模型相反 |
| 主键 | UUID（`GenerationType.UUID`） | `bigint auto_increment` | 全表改造 |
| 时间 | `OffsetDateTime` | `Date` + `@JsonFormat(GMT+8)` | 类型不同 |
| 数据库 | H2 / PostgreSQL | **MySQL** | 方言差异 |
| 分表 | 无 | `TableNameDynamic` | — |

**核心矛盾**：规范因多数据源**禁止事务**（靠幂等/补偿）；本项目场景线（②④⑨⑩）**全靠事务**。

---

## 四、中间件

| 项 | 本项目 | 规范 |
|---|---|---|
| 分布式锁 | **手写 `SET NX EX` + Lua** | **RedissonUtil 统一入口** |
| 本地缓存 | 无 | **Caffeine `LoadingCache`** |
| Kafka | 单集群、单条消费 | **7 个集群** + 批量/单条 ContainerFactory + `autoStartup` 开关 |
| 调度 | **`@Scheduled`** | **XXL-Job**（`@XxlJob` + `ReturnT`） |
| 文件存储 | 无 | COS + EasyExcel |

---

## 五、工程工具（可低成本补齐）

| 规范要求 | 现状（已核实） | 工作量 |
|---|---|---|
| Lombok（`@Slf4j`/`@Data`） | 依赖存在、**零使用**；手写 `LoggerFactory.getLogger` **31 处** | 1 天 |
| MapStruct | 缺失（手写 Mapper） | 半天 |
| Knife4j | 缺失 | 2 小时 |
| `ErrorEnum` 错误码 | 缺失（`IllegalStateException`） | 半天 |
| Redis Key 常量化 | **硬编码 1 处** | 1 小时 |
| COS + EasyExcel | 缺失 | 视需求 |

---

## 六、编码红线对照

| 红线 | 本项目 | 状态 |
|---|---|---|
| 构造注入 + final | 全部构造器注入 | ✅ |
| Controller 不写业务 | ✅ | ✅ |
| POST `@RequestBody` / GET `@RequestParam` | ✅ | ✅ |
| `@Lazy` 解循环依赖 | ✅ | ✅ |
| 禁止 `printStackTrace` | 0 处 | ✅ |
| Service 不返回统一壳 | ✅ | ✅ |
| 禁止 `System.out.println` | **5 处** | ❌ |
| 日期 `Date` + GMT+8 | 用 `OffsetDateTime` | ⚠️ 更优但不符 |
| 返回 `ReturnMsg<T>` | `ApiResponse<T>` | ⚠️ 名称不同 |
| 审计 `ZbLogUtil` | 自建 `@AuditLog` | ⚠️ 等价不同源 |
| Controller 分端 | 单 Controller | ❌ |
| 禁止业务方法 `@Transactional` | **22 处** | ❌ 架构冲突 |

---

## 七、本项目领先项（规范未涉及）

| 项 | 本项目 |
|---|---|
| 测试 | JUnit 5 + Mockito + AssertJ（13 run 全绿） |
| 容器化 | Dockerfile 多阶段 + `.dockerignore` + 镜像瘦身 |
| 可观测 | Actuator + 自定义 metrics |
| 文档 | 12 课讲义 + 问答记录 |

---

## 八、建议

**不建议硬对齐**（≈ 重写项目）。

**低成本对齐 6 项**（2-3 天，不破坏架构）：
1. Lombok `@Slf4j` 替换 31 处手写 Logger
2. `ErrorEnum` 错误码
3. `RedisConstant`（去硬编码 key）
4. 清理 5 处 `System.out.println`
5. Knife4j 接口文档
6. 统一返回体命名（`ApiResponse` ↔ `ReturnMsg`）

**分场景判断**：

| 目标 | 做法 |
|---|---|
| 独立产品/教学 | 保持现状（技术栈更新、架构更清晰） |
| 并入 live-java 体系 | 按规范**新建项目**，业务逻辑迁移 |
---

## 九、补充：为什么规范禁止业务方法加事务（2026-10-24 学员追问）

### 9.1 根本原因：事务和 `@DS` 抢同一个连接

```text
① @DS 原理：AOP 切面在方法执行前把数据源名放进 ThreadLocal
② 事务原理：@Transactional 开启时从【当前数据源】取 Connection 并绑定
③ 冲突：事务先开（连接已绑定 master）→ 内层再 @DS → SQL 仍走 master
        → 数据写错库（最高危事故）
```

**顺序决定成败**：`@DS` 在事务外层（优先级高）才生效；事务先开则 `@DS` 白切。

### 9.2 他们在哪里加事务

```java
@Override
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
@DS(CommonConstant.TD_INTEGRAL)
public boolean saveBatchNew(List<Entity> entityList) {
    String sqlStatement = getSqlStatement(SqlMethod.INSERT_ONE);
    return executeBatch(entityList, 1000, (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
}
```

| 设计 | 为什么 |
|---|---|
| `@DS` + `@Transactional` **同方法** | 保证"先切库 → 再开事务" |
| **`REQUIRES_NEW`** | 挂起外层、新建独立事务 → 新连接 → `@DS` 生效（`REQUIRED` 会沿用外层连接而失效） |
| **只用于"单库批量写"方法** | 明确写哪个库，不涉及跨库一致性 |

### 9.3 业务层不加事务，一致性靠什么

| 手段 | 说明 |
|---|---|
| 幂等设计 | 唯一键防重 |
| 先写后查校验 | 写完立即验 |
| 失败补偿 | 写明细 + 对账/修复 Job |
| 分布式锁 | Redisson 防并发 |

**最终一致模型**：跨库不实时强一致，靠对账收敛。

### 9.4 规范还禁了 IService 批量方法

```text
禁止 saveBatch / saveOrUpdateBatch / updateBatchById / saveOrUpdate
原因：未显式绑定数据源 → 多数据源下写默认库（写错库）
替代：saveBatchNew / updateBatchByIdNew（显式 @DS + REQUIRES_NEW）
```

### 9.5 与本项目的对比

| | 本项目 | 规范 |
|---|---|---|
| 数据源 | 1 个 | 15 个 |
| 事务 | 22 处正常使用（强一致） | 业务层禁止，只批量方法 `REQUIRES_NEW` |
| 一致性 | 强一致（事务回滚） | 最终一致（幂等 + 补偿 + 对账） |

**取舍**：15 个库的系统里，"数据不能写错地方"优先于"强一致"。

---

## 十、补充：`@DS` 是什么（2026-10-24 学员追问）

### 10.1 定义

`@DS` = `@DataSource` 缩写，来自 **`dynamic-datasource-spring-boot-starter`**（baomidou 出品，与 MyBatis-Plus 同源）。

```java
@Service
@DS("slave")                                    // 类级默认
public class UserService {

    @DS("master")                               // 方法级优先
    public void save(User user) { ... }

    public User get(Long id) { ... }            // 走类级 slave
}
```

### 10.2 为什么需要

| 场景 | 说明 |
|---|---|
| 读写分离 | 写 master、读 slave |
| 分库 | 订单库 / 积分库 / 用户库 |
| 多租户 | 一租户一库 |

### 10.3 工作机制（三步）

```text
① AOP 拦截：DynamicDataSourceAnnotationInterceptor 拦到 @DS
② ThreadLocal：DynamicDataSourceContextHolder.push("slave")
③ 路由：AbstractRoutingDataSource.determineCurrentLookupKey()
        → 从 Map<String, DataSource> 选对应【连接池】
```

**切换数据源 = 换连接池**（每个库独立 HikariCP）。

```text
DataSource（Spring 接口）
  └── AbstractRoutingDataSource（按 key 路由）
        └── DynamicRoutingDataSource
              ├── master → HikariCP
              ├── slave  → HikariCP
              └── tdIntegral → HikariCP
```

### 10.4 四种粒度（就近原则）

```text
方法 > 类 > Mapper 接口 > 全局默认（spring.datasource.dynamic.primary）
```

### 10.5 配置与 strict

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: true          # ★ 找不到数据源就报错（防写错库）
      datasource:
        master: { url: ..., username: ... }
        slave:  { url: ... }
```

`strict: false` 时名字写错会静默用默认库 → 数据写错地方；`strict: true` 直接报错。

### 10.6 五个坑

| 坑 | 现象 | 解法 |
|---|---|---|
| 事务内切换 | @DS 失效、写错库 | `REQUIRES_NEW` |
| 异步/线程池 | 子线程丢 ThreadLocal | 手动传递上下文 |
| 名字写错 | 静默走默认库 | `strict: true` |
| IService 批量方法 | saveBatch 走默认库 | 自建 `XxxNew` + `@DS` |
| 同类自调用 | AOP 不生效（SP2 老坑） | 拆类 / `@Lazy` |

### 10.7 与本项目

| | 本项目 | 规范 |
|---|---|---|
| 数据源 | 1 个 | 15 个 |
| @DS | 0 处（不需要） | 到处用 |

体验多数据源的两条路：手写 `AbstractRoutingDataSource`（教学价值高，能看清底层）或直接引 `dynamic-datasource`（生产快）。

---

## 十一、补充：中间件差异的原因与双向收益（2026-10-24 学员追问）

### 11.1 总纲：中间件选型 = f(规模)

```text
规模小（本项目）                     规模大（live-java）
单实例、1 个库                       多实例、15 个库
QPS 几百                             QPS 数万~数十万
1 个定时任务                         几十个定时任务
1 把锁                              几百处加锁
1 个 Kafka                          7 个集群
  ↓                                    ↓
简单直接够用（过度设计是负担）         必须平台化（手写就是事故源）
```

### 11.2 逐项原因

#### ① 分布式锁：手写 vs Redisson

| | 我们 | 他们 |
|---|---|---|
| 场景 | 1 把锁（任务互斥） | 几百处加锁 |
| 能力 | `SET NX EX` + token + Lua | 加看门狗续期 / 可重入 / 公平锁 / 读写锁 / 信号量 |
| 关键 | 业务超时靠幂等兜底 | 看门狗保证不误过期 |

**他们必须统一入口**：规范原话"业务模块禁止自行复制副本"——几百处手写 = 几百个事故点。
**我们手写的收益**：完全理解原理（原子性、token、Lua 每一步为什么必要）。

#### ② 本地缓存：无 vs Caffeine

```text
三层缓存：Caffeine（~100ns）→ Redis（~1ms）→ DB（~10ms）
```

- 他们：规范红线"高并发 h5 接口禁止直查数据库"，热点数据本地兜；
- 我们：单实例 + 低 QPS，Redis 的 1ms 不构成瓶颈 → Caffeine 是过度设计。

#### ③ Kafka：单集群 vs 7 集群

| 差异 | 原因 |
|---|---|
| 7 集群 | 业务隔离（优惠券洪峰不冲垮主业务）+ 历史遗留 + 容量隔离 |
| `autoStartup` 开关 | 故障时只停某个消费者，不改代码不重启 |
| 批量 ContainerFactory | 批量拉取吞吐高，但实现复杂 |

**我们值得学的一条**：`autoStartup` 开关（1 小时可加）。

#### ④ 调度：@Scheduled vs XXL-Job

| 能力 | @Scheduled | XXL-Job |
|---|---|---|
| 可视化管理 | ❌ | ✅ |
| 失败重试 + 告警 | ❌ | ✅ |
| 分片广播 | ❌ | ✅ |
| 运行参数 | 要重启 | 界面填 |
| 多实例只跑一次 | 自己写锁（场景⑩） | 调度中心保证 |

**我们不用 XXL-Job 的原因**：1 个任务不值得部署调度中心（多一个服务 + 一个库）。

#### ⑤ 文件存储：无 vs COS + EasyExcel

纯业务驱动——没有文件上传需求就不该引。

### 11.3 对比的双向收益

**从规范能学到 4 条**：

| 学什么 | 用在哪 |
|---|---|
| 统一入口纪律（禁止各处复制锁实现） | `DistributedTaskLock` 已统一，可再通用化 |
| `autoStartup` 开关 | Kafka 消费者故障隔离 |
| 批量消费 | 任务量上来后提升吞吐 |
| 分片调度 | 清理数据量大时用 |

**我们领先规范的 4 条**：

| 我们有什么 | 规范 |
|---|---|
| 手写锁的完整原理 | 直接调 RedissonUtil（黑盒） |
| Actuator + metrics | 未涉及 |
| 测试体系（13 run） | 仅"测试通过" |
| Docker 容器化 + 镜像瘦身 | 未涉及 |

### 11.4 升级信号表

| 信号 | 升级方向 | 成本 |
|---|---|---|
| 锁场景 > 3 种 | Redisson | 半天 |
| 定时任务 > 5 个、要历史/告警 | XXL-Job | 1 天 |
| 消费者 > 5 个、要独立启停 | autoStartup + 多集群 | 1 小时~1 天 |
| 热点 QPS > 1000、Redis 吃紧 | Caffeine | 半天 |
| 有文件上传/导出 | COS + EasyExcel | 按需求 |

### 11.5 结论

```text
中间件没有"先进/落后"，只有"匹配/不匹配"：
  单实例 → 手写锁 / @Scheduled / 单集群 = 正确
  多实例多业务 → Redisson / XXL-Job / 7 集群 = 正确

真正的收获不是照抄选型，而是记住【什么信号出现时该换什么】。
```
