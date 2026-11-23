# T9 · XXL-Job 分布式任务调度

> **定位**：把场景⑩ 的"@Scheduled + Redis 分布式锁"自研调度，升级为调度中心平台化方案。本课回答三件事：**XXL-Job 是什么/怎么跑起来/什么时候才值得引入**。
> **前置**：场景⑩（[⑩定时清理](../业务场景实战/⑩定时清理.md)）、T7（Redisson）、[分布式并发全景](../服务架构/分布式并发全景——有序性原子性互斥.md)、[实战案例·七件套拆解](实战案例——分布式协调机制七件套拆解.md)（§3.7 已预览 XXL-Job）
> **建立**：2026-11-20

---

## 0. 五步闭环进度（本课状态）

| 步骤 | 状态 | 说明 |
|------|------|------|
| ① 设计/审计 | ✅ | 本篇 §1（@Scheduled+锁 的四个痛点 = 换工具的信号） |
| ② 编码（最小 demo） | ✅ | 已写入：pom 依赖 + `XxlJobConfig`（`xxl.job.enabled` 开关，默认关闭）+ demo 包 `XxlJobDemoHandler` |
| ③ 实测 | ✅ | 2026-11-23 闭环：调度日志每 10 秒成功一条（见 §3.6.3 实测数据） |
| ④ 讲义落档 | ✅ | 本文档 |
| ⑤ 提交 | ⬜ | 等学员发话 |

> **特殊说明**：本项目当前 @Scheduled+锁 够用（总纲判断依据："单实例下 XXL-Job 是过度设计"）——所以 demo **默认关闭**（`@ConditionalOnProperty`），引入依赖不破坏现有启动。**学的是"什么信号出现时该换什么"**。

---

## 1. 设计/审计：@Scheduled + 锁 的六个痛点（换工具的信号）

项目现状：[`RetentionCleanupJob`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java)）——`@Scheduled` fixedDelay 1 小时 + Redis 锁防多实例重复 + 异常吞掉记日志。

这套自研方案**能跑、多实例安全**，但有六个"长大后会疼"的痛点：

| # | 痛点 | 现状 | XXL-Job 的解法 |
|---|------|------|----------------|
| 1 | **频率写死** | `interval-ms` 在 yml——改频率要改配置重启 | 控制台改 Cron，**保存即生效**（不用重启） |
| 2 | **无法手动触发/补跑** | 深夜失败只能等下一小时 | 控制台"执行一次"按钮（带执行参数） |
| 3 | **失败无告警** | 异常只进日志（`log.error` 后被吞） | 失败自动重试 N 次 + 邮件/Webhook 告警 |
| 4 | **无执行历史** | 翻日志文件才知道结果 | 控制台执行日志列表（耗时/结果/异常堆栈） |
| 5 | **无法启停** | 停止任务 = 重启应用 | 控制台一键启动/停止 |
| 6 | **分片并行难** | 多实例同跑一段逻辑靠锁互斥 | **分片广播**：所有实例同时拿不同分片并行 |

**什么时候该换（信号清单，对照总纲"选型 = f(规模)"）**：

```text
出现任意两条 → 引入调度平台（XXL-Job / PowerJob）：
  ✅ 定时任务 ≥ 5 个，且 cron 经常被运营要求调整
  ✅ 出现过"半夜失败了，第二天才发现"（需要告警/重试）
  ✅ 需要手动触发/临时补跑（运营/运维提需求）
  ✅ 单个任务大到需要分片并行（百万级数据分批处理）
  ✅ 多实例部署，且 @Scheduled+锁 已出过重复执行/漏执行事故
一个信号都没有 → 留在 @Scheduled+锁（当前状态 ✓）
```

---

## 2. XXL-Job 是什么：架构与执行流程

### 2.1 两大组件

```text
┌─────────────────────────────────┐        ┌──────────────────────────────┐
│ 调度中心（xxl-job-admin）          │  REST  │ 执行器（你的 Spring Boot 应用）  │
│ 独立部署的 Web 平台                │ ─────→ │ · 内嵌 server（默认 9999 端口） │
│ · 任务管理（cron/参数/启停）        │        │ · 接收调度 → 起线程跑 @XxlJob  │
│ · 路由策略/阻塞策略/重试/告警        │        │ · 自动注册到调度中心            │
│ · 执行日志收集                     │ ←───── │ · 回调执行结果                 │
└─────────────────────────────────┘  回调   └──────────────────────────────┘
        依赖 MySQL（存任务/日志）                     业务代码只写 @XxlJob 方法
```

**交互流程（四条线，上图组件视角的动态版）**：

```text
执行器（你的应用内嵌，9999 端口）              调度中心（xxl-job-admin 独立部署 + MySQL）
──────────────────────────────              ──────────────────────────────
① 启动 → 注册（appname + ip:port）──────────►  执行器列表（谁在线一目了然）
                                              ② Cron 到点 → 路由策略选一个实例
③ 收到派单 → 起线程跑 @XxlJob ◄──────────────  REST 调用（jobId + 分片参数）
④ 执行完 → 回调结果（成功/失败/耗时）──────────►  写执行日志
⑤ 失败 → 自动重试 N 次 → 仍失败 ──────────────►  告警（邮件/Webhook）

业务代码只写 @XxlJob 方法                       控制台：改 Cron/启停/手动触发/看日志
```

- **调度中心**：独立部署的 war/jar（连接自己的 MySQL `xxl_job` 库），负责"什么时候触发、触发给谁、失败怎么办"；
- **执行器**：你的应用引入 `xxl-job-core` 后内嵌的小 server——启动时自动注册到调度中心（"我在，ip:port 是 xxx"），等派单。

### 2.2 与 @Scheduled 的本质区别（一句话）

> **@Scheduled = 每个实例自己定闹钟**（所以需要 Redis 锁防止重复执行——场景⑩）；
> **XXL-Job = 中心定闹钟 + 派单给一个实例**——调度互斥**天然成立，不需要锁**（七件套拆解 #7 的语义）。
> 例外：**分片广播**会派给所有实例（每个实例拿不同分片号并行）——此时幂等仍要自己做（§4.4）。

**直观对照**：

```text
@Scheduled（各自定闹钟）                  XXL-Job（中心派单）
──────────────────────                 ────────────────────
实例A 闹钟到点 → 抢 Redis 锁 → 成功执行    admin 到点 → 派单给实例A（轮询）
实例B 闹钟到点 → 抢锁失败 → 跳过          实例B/C 根本不会被触发
实例C 闹钟到点 → 抢锁失败 → 跳过
= 3 个实例抢 1 个机会，必须自己带锁       = 天然只有一个执行者，锁免了
```

### 2.3 执行流程（一次调度的完整生命周期）

**时序图**：

```text
时间 ─────────────────────────────────────────────────────────────►
admin      ① Cron 到点
           ② 按路由策略选实例（轮询/故障转移/分片广播…）
           ③ REST 派单 → http://实例A:9999/run（jobId + 分片参数）
                                              │
执行器A                          ④ 起线程执行 @XxlJob 方法（阻塞策略生效）
                                 ⑤ 执行完 → 回调结果
admin      ⑥ 写执行日志（成功/失败/耗时）
           ⑦ 失败 → 按配置重试 N 次 → 仍失败 → 告警邮件/Webhook
```

**文字步骤**：

```text
① 调度中心按 Cron 到点 → 生成调度记录
② 按路由策略选出目标实例（轮询/故障转移/分片广播…）
③ REST 调用目标执行器（带 jobId + 分片参数）
④ 执行器起线程执行对应 @XxlJob 方法（超时/阻塞策略生效）
⑤ 执行完成 → 回调调度中心 → 写执行日志（成功/失败/耗时）
⑥ 失败 → 按配置重试 N 次 → 仍失败 → 告警邮件/Webhook
```

---

## 3. 最小 demo（已写入项目，默认关闭）

### 3.1 依赖（pom.xml 已加）

```xml
<!-- 分布式任务调度（T9）：XXL-Job 执行器——调度互斥/分片广播/失败告警（默认关闭，xxl.job.enabled=true 时生效） -->
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>2.4.1</version>
</dependency>
```

### 3.2 配置（两处：主配置全局默认 + dev-docker 调试覆盖）

> ★ **实测踩坑（2026-11-23）**：XXL-Job **不是 Spring Boot 官方 starter**，配置前缀 `xxl.job.*` 必须放在 **yml 顶层**（与 `spring:` 平级）——放进 `spring:` 段会变成 `spring.xxl.job.enabled`，`@ConditionalOnProperty(name = "xxl.job.enabled")` 读不到 → **执行器 Bean 不创建、9999 无监听、日志里没有任何 xxl-job 字样**（本项目已踩：症状 = 应用正常启动但执行器"消失"）。

**主配置 application.yml**（全局默认 `enabled: false`，位于 `server:` 之前的顶层）：

```yaml
# ===== T9 分布式任务调度（XXL-Job 执行器）=====
# ★ 配置必须在顶层（xxl: 与 spring: 平级）
# 默认关闭；dev-docker 调试环境在 application-dev-docker.yml 覆盖 enabled=true
xxl:
  job:
    enabled: false                                      # ★ 打开 = 接入执行器
    admin-addresses: http://localhost:8082/xxl-job-admin
    access-token: default_token                         # ★ 必须与调度中心一致
    executor:
      appname: research-agent-executor                  # 执行器名字（控制台新建执行器时填同名）
      port: 9999                                        # 执行器内嵌 server 端口
      logpath: ./logs/xxl-job
      logretentiondays: 7
```

**调试环境 application-dev-docker.yml**（覆盖 `enabled`——你用 dev-docker profile 运行调试；起调度中心容器后改 true）：

```yaml
xxl:
  job:
    enabled: false          # ★ 起调度中心容器后改 true；未起时保持 false（否则启动刷注册失败警告）
    admin-addresses: http://localhost:8082/xxl-job-admin
    access-token: default_token
    executor:
      appname: research-agent-executor
      port: 9999
      logpath: ./logs/xxl-job
      logretentiondays: 7
```

**compose 内的 backend 容器**（application-docker.yml 已配 `enabled: true`，容器名寻址 `http://xxl-job-admin:8080/...`——开箱即用）。

### 3.3 执行器配置类（新增 [`XxlJobConfig`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/XxlJobConfig.java)）

`@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true")`——**不开开关，这个 Bean 不存在，应用行为与现在完全一致**。

### 3.4 demo handler（新增 [`XxlJobDemoHandler`](../../backend/src/main/java/com/aiexplorer/researchagent/demo/XxlJobDemoHandler.java)）

三个 `@XxlJob` 方法：`demoHelloJob`（hello）/ `demoShardingJob`（分片广播演示）/ `retentionCleanupJobHandler`（场景⑩ 迁移样板）。

### 3.5 实跑清单（compose 双容器，预计 15 分钟）

```bash
# ① 起双容器（MySQL + 调度中心）——SQL 已在仓库 backend/docker/xxl-job/ 挂载，首次启动自动建表
cd backend/docker
docker compose up -d mysql-xxl xxl-job-admin
#    控制台：http://localhost:8082/xxl-job-admin（登录 admin/123456）
#    ★ 端口 8082 是刻意选的——8081 已被 Adminer（tools profile）声明，避开冲突

# ② 改 application-dev-docker.yml：xxl.job.enabled 改 true（你用 dev-docker profile 运行调试）

# ③ 重启 backend（dev-docker profile）→ 启动日志确认：
#    The following 1 profile is active: "dev-docker"
#    >>>>>>>>>>> xxl-job remoting server start success ... port=9999
#    → 控制台"执行器管理"应出现 research-agent-executor（OnLine 机器 1 台）

# ④ 控制台"任务管理"→ 新建任务：
#    执行器 = research-agent-executor / JobHandler = demoHelloJob
#    Cron = 0/10 * * * * ?（每 10 秒）/ 路由 = 轮询 → 启动

# ⑤ 看"调度日志"：每 10 秒一条成功 → demo 闭环 ✅
#    再试"执行一次"按钮（手动触发）、停止/启动按钮（§1 痛点 2/5 消失的体感）
```

> **为什么必须有 MySQL**：调度中心的一切数据（任务配置/执行日志/用户账号）都存在 MySQL 的 `xxl_job` 库——XXL-Job admin 没有"无数据库"模式。compose 方案已把它容器化 + 自动建表（挂载 `/docker-entrypoint-initdb.d/`），你唯一要跑的命令就是 `docker compose up`。
>
> **两条运行路径都已配好**：本地 mvn（dev-docker：`localhost:8082`）与 compose 内 backend 容器（application-docker.yml：`http://xxl-job-admin:8080/...`，enabled=true 开箱即用）。

### 3.6 控制台操作流程实录：新建任务 → 启动 → 调度日志 → 执行日志（2026-11-23 实测闭环 ✅）

> 本节是 §3.5 实跑清单的**实测记录**——2026-11-23 已完整走通：调度日志出现每 10 秒一条的成功记录（见 3.6.3 实测数据）。

#### 3.6.1 新建任务表单逐字段（"任务管理 → 新增任务"）

| 字段 | 填什么 | 说明 |
|------|--------|------|
| **执行器** ★ | 下拉选 **`research-agent-executor`** | ❌ 别选"示例执行器"（初始化 SQL 的假条目）——demo handler 注册在我们的执行器下 |
| 任务描述 / 负责人 | 随意 | 自用 |
| 报警邮件 | 留空 | 学习环境无邮件服务器 |
| 调度类型 / Cron | `CRON` / `0/10 * * * ?` | 每 10 秒；`0,10 * * * ?` 也合法（每分钟第 0、10 秒各一次）——**6 位，无年份** |
| 运行模式 | `BEAN` | GLUE = 在线写代码模式，不用 |
| **JobHandler** ★ | `demoHelloJob` | ★ 必须与 `@XxlJob("demoHelloJob")` 注解值**完全一致** |
| 任务参数 | 留空 | `XxlJobHelper.getJobParam()` 可读取 |
| 路由策略 | `第一个`（单实例） | 多实例换"轮询" |
| 调度过期策略 | `忽略` | 错过的调度不补跑 |
| 阻塞处理策略 | `单机串行` | 上次没跑完本次排队 |
| 任务超时时间 / 失败重试次数 | 留空（0） | demo 秒回不重试 |

#### 3.6.2 三个关键按钮（保存 ≠ 启动！）

| 按钮 | 位置 | 作用 |
|------|------|------|
| **保存** | 表单底部 | 只创建/更新任务——**默认是停止状态，不会触发！** |
| **启动** | 任务列表"操作"下拉 | 开始按 Cron 调度（状态列变"运行中"） |
| **执行一次** | 任务列表"操作"下拉 | 手动立即触发（带可选参数）——不用等 Cron |

#### 3.6.3 调度日志页逐列解读（实测数据，2026-11-23）

"调度日志"页每列的含义 + 当天实测：

| 列 | 含义 | 实测 |
|----|------|------|
| 任务 ID | 任务主键 | 2（demoHelloJob）/ 1（另一条） |
| 调度时间 | admin 触发的时刻 | 任务 2：11:14:54 → 11:16:00 → 11:16:10 → 11:17:00 → 11:17:10…（**正好对齐 Cron `0/10`：每分钟第 0、10 秒**） |
| 调度结果 | admin → 执行器的**派单**是否成功 | 全部"成功" ✅（失败常见原因：执行器地址不可达 → 见 3.6.5） |
| 调度备注 | 派单详情（点"查看"） | 路由结果、派单地址 |
| 执行时间 / 执行结果 | 执行器**业务执行**的状态 | 全部"成功" ✅ |
| 执行备注 | 执行详情（点"查看"→ 执行日志页签 = **`XxlJobHelper.log` 的输出就在这里**） | "hello from XXL-Job! 执行参数：…" |
| 操作 | 下拉：执行日志 / 终止任务 / 查询 | 排障入口 |

**实测结论（2026-11-23，9 条记录）**：

```text
任务 2（demoHelloJob）：11:14~11:18 每 10 秒一条"调度成功 + 执行成功" → Cron 调度闭环 ✅
任务 1：10:43:23 一条"失败"（红） → ★ 这是配置整改【之前】的历史记录
        （当时 yml 配置位置错误 → 执行器未启动 → 派单 timeout）——留着当新旧对照，
         也可在任务列表删掉该任务后清理日志
```

#### 3.6.4 ★ 日志去向：`XxlJobHelper.log` 不进 IDE 控制台（认知纠偏）

```java
XxlJobHelper.log("hello from XXL-Job! ...")   // → 调度中心网页的"执行日志"（存 admin 数据库）
log.info("...")                                // → backend 的 IDE 控制台（SLF4J 标准日志）
```

**两者并存，各看各的**：

```java
@XxlJob("demoHelloJob")
public void demoHelloJob() {
    String jobParam = XxlJobHelper.getJobParam();
    log.info("[XXL-Job] demoHelloJob 触发，参数={}", jobParam);      // → IDE 控制台（类上加 @Slf4j，T1）
    XxlJobHelper.log("hello from XXL-Job! 执行参数：{}", jobParam);  // → 调度中心网页执行日志
    XxlJobHelper.handleSuccess();
}
```

> 初学者最容易在这里误判："IDE 控制台没打印 = 任务没执行"——实际任务已成功，日志在网页上。

#### 3.6.5 失败记录排查法

```text
调度日志某条"失败" → 点"查看"：
  ├─ 调度备注：admin → 执行器的派单详情（timeout = admin 连不上执行器地址）
  ├─ 执行备注/执行日志：执行器收到后业务抛的异常堆栈
  └─ 常见：调度 timeout = 执行器上报 IP 容器不可达（修：XxlJobConfig.setIp("host.docker.internal")）
```

#### 3.6.6 常用管理操作速查

| 操作 | 位置 | 说明 |
|------|------|------|
| 启动/停止任务 | 任务列表"操作"下拉 | 停止 = 不再调度（不删任务） |
| 执行一次 | 同上 | 手动触发（补跑/立即验证） |
| 删除任务 | 同上 | 任务删除后调度日志保留（可追溯） |
| 查询/过滤 | 调度日志顶部 | 按任务/状态/时间范围过滤 |
| 清理日志 | 调度日志右上"清理" | 按时间清理历史日志（防 xxl_job_log 表暴涨，§7 坑 10） |

---

## 4. 核心配置详解

### 4.1 路由策略（8 种，派单给谁）

| 策略 | 行为 | 适用 |
|------|------|------|
| 第一个 / 最后一个 | 固定派给注册列表的第一个/最后一个 | 测试 |
| 轮询 | 依次轮流 | 均衡负载（默认常用） |
| 随机 | 随机挑一个 | 均衡负载 |
| 一致性 HASH | 按任务参数 hash 定实例 | 同任务总是同一实例（有状态） |
| 故障转移 | 先心跳检测，挑活着的 | 高可用要求 |
| 忙碌转移 | 依次尝试，谁闲派谁 | 高可用 + 均衡 |
| **分片广播** | **派给所有实例**，带分片参数（index/total） | 大任务并行（§4.3） |

### 4.2 阻塞策略（上一次没跑完，这次又到点了怎么办）

| 策略 | 行为 | 适用 |
|------|------|------|
| 单机串行（默认） | 本次调度进入该执行器的队列排队 | 任务必须逐次执行、不能丢 |
| 丢弃后续 | 本次触发直接丢弃 | 上一次结果仍有效、不需要追跑（如"每分钟刷缓存"） |
| 覆盖 | 杀掉正在执行的，跑本次 | 上一次已无意义（如"全量刷新"） |

**选错的现象**：任务耗时 5 分钟 + Cron 每 1 分钟 + 单机串行 → 队列越堆越多（调度堆积告警）——**频率必须 > 任务耗时**，或改丢弃。

### 4.3 分片广播（XXL-Job 的王牌能力）

```java
@XxlJob("demoShardingJob")
public void demoShardingJob() {
    int shardIndex = XxlJobHelper.getShardIndex();   // 我是第几片（0 开始）
    int shardTotal = XxlJobHelper.getShardTotal();   // 总共几片 = 在线实例数
    // ★ 分片并行：比如 100 万条数据，按 id % shardTotal == shardIndex 分给各实例
}
```

**分片广播图解**：

```text
admin：分片广播触发（在线实例 3 个 → shardTotal = 3）
   ├─► 实例A  index=0  → 处理 id % 3 == 0 的数据（~33 万条）
   ├─► 实例B  index=1  → 处理 id % 3 == 1 的数据（~33 万条）
   └─► 实例C  index=2  → 处理 id % 3 == 2 的数据（~33 万条）
= 100 万条数据，吞吐从 1× 变 3×；对比场景⑩ 锁方案 = 只有一台干活
```

**与场景⑩ 锁方案的对比**：锁方案 = 所有实例抢一个任务（串行）；分片广播 = **所有实例同时跑，各拿一片（并行）**——大任务的吞吐从 1× 变 N×。

### 4.4 ★ 幂等提醒（调度互斥 ≠ 永不重复）

调度中心网络抖动可能**重试派发**、故障转移可能**换实例重跑**——`@XxlJob` 方法内部**仍要保持幂等**（状态守卫/唯一约束，场景④⑥ 的套路原样适用）。调度平台的互斥解决"重复调度"，解决不了"业务重复执行"。

---

## 5. 能力对比表（本线约定：三列）

| 能力 | 本项目现状（@Scheduled+锁） | XXL-Job | 第三方案 |
|------|---------------------------|---------|---------|
| 触发配置 | yml/代码（改要重启） | 控制台 Cron 即时生效 | PowerJob（工作流/秒级）、K8s CronJob（容器级） |
| 手动触发/补跑 | ❌ | ✅ 控制台执行一次 | PowerJob ✅ |
| 失败重试 | ❌（自己写） | ✅ 次数可配 | Quartz ❌ |
| 告警 | ❌ | ✅ 邮件/Webhook | PowerJob ✅（钉钉/飞书） |
| 执行历史 | ❌（翻日志） | ✅ 日志列表 | PowerJob ✅ |
| 启停管理 | ❌ | ✅ 一键启停 | K8s CronJob（改对象） |
| 分片并行 | ❌（要自己写） | ✅ 分片广播 | PowerJob ✅ |
| 依赖 | 无（JVM 内） | **调度中心独立部署 + MySQL** | Quartz 仅 DB、PowerJob 亦需部署 |
| 多实例安全 | 手写锁兜底 | 平台派单天然互斥 | Quartz 集群模式（DB 锁） |

**第三方案一句话**：Quartz（老牌、Java 内嵌、无控制台）；PowerJob（新一代、功能超 XXL-Job、生态略小）；K8s CronJob（容器级、无执行日志、秒级做不到）——**企业主流仍是 XXL-Job（中文文档/部署简单/大厂验证）**。

---

## 6. 场景⑩ 迁移路线（如果信号出现，真要迁）

```text
第 1 步（双轨期）：写 retentionCleanupJobHandler（demo 已含样板），
      调用同一个 RetentionCleanupService.cleanupOnce()——新旧并存
第 2 步（切流）：控制台建任务（Cron 对齐原 1 小时）→ 启动观察几天
      → 同时把 @Scheduled 的 interval 调大（或注释）避免双跑
第 3 步（收尾）：删除 @Scheduled 入口 + DistributedTaskLock 注入（handler 内不再需要锁）
      —— 保留 cleanupService 的幂等逻辑（调度重试仍需要）
```

**迁移后 `RetentionCleanupJob` 类可以删掉**——但 `cleanupOnce()` 的幂等/批量逻辑原样保留（XXL-Job 只接管"何时触发、派给谁"）。

---

## 7. 常见坑速查

| # | 坑 | 现象 | 解法 |
|---|-----|------|------|
| 1 | **配置放进 `spring:` 段** | 执行器 Bean 不创建、9999 无监听、日志无 xxl-job 字样（应用照常启动——静默失败） | ★ **`xxl.job.*` 必须在 yml 顶层**（本项目 2026-11-23 实测踩坑） |
| 2 | accessToken 不一致 | 执行器注册失败（控制台看不到机器） | 双方 yml/配置统一 token |
| 3 | 执行器条目未在控制台新建 | 执行器管理里看不到（心跳已到但无条目） | 控制台"新增执行器"（AppName 与 yml appname 一致，自动注册） |
| 4 | 执行器端口 9999 被占/不可达 | 注册成功但调度失败 | 换端口 / 确认容器网络 |
| 5 | 调度中心与执行器**时钟不同步** | 调度延迟/乱序 | 容器/机器统一 NTP |
| 6 | 阻塞策略选错 | 任务堆积（单机串行）或结果被覆盖 | 按任务语义选（§4.2） |
| 7 | 分片广播 + 非幂等任务 | 重复派发 → 数据错乱 | handler 内保持幂等（§4.4） |
| 8 | 调度中心单点 | admin 挂了全部任务停摆 | 生产部署 ≥2 实例（共享同一 MySQL） |
| 9 | Cron 写成 7 位（Quartz 格式） | XXL-Job 用 6 位（秒 分 时 日 月 周） | 去掉年份位 |
| 10 | 执行日志暴涨 | 高频任务日志堆积 | 控制台清理 + logretentiondays 配置 |
| 11 | 日志去向误解 | `XxlJobHelper.log` 不进 IDE 控制台 → 误判"任务没执行" | 它写调度中心网页执行日志；IDE 输出用 `log.info`（§3.6.4） |

---

## 8. 验收题（答不出来 = 没学透）

1. XXL-Job 的调度互斥和场景⑩ 的 Redis 锁互斥，**本质区别**是什么？（提示：中心派单 vs 各自定闹钟）
2. 阻塞策略"单机串行 / 丢弃后续 / 覆盖"分别适合什么任务？各举一例。
3. 分片广播的 handler 里 `shardIndex/shardTotal` 怎么用？为什么**仍需要幂等**？
4. 列出三个"该把 @Scheduled+锁 换成调度平台"的信号；再说出一个"不该换"的场景。

---

## 9. 本课问答记录

| 日期 | 问题 | 结论 |
|------|------|------|
| 2026-11-23 | 执行器管理里看不到 research-agent-executor | 三层排查：①控制台需手动"新增执行器"（自动注册只报机器地址）②backend 需重启且 profile=dev-docker ③**yml 的 xxl 配置必须顶层**（本项目踩坑：放 spring: 段 → spring.xxl.job.enabled 读不到，已修复） |
| 2026-11-23 | 实跑一定要装 MySQL 吗 | 是（admin 强依赖）——compose 双容器已容器化（mysql-xxl 自动建表） |
| 2026-11-23 | 执行器管理里看不到 research-agent-executor | 三步：控制台手动"新增执行器"条目 + backend 重启 + xxl 配置放 yml 顶层（坑 1/3） |
| 2026-11-23 | demoHelloJob 没有打印 | XxlJobHelper.log 写调度中心网页执行日志（非 IDE 控制台）；IDE 输出用 log.info（§3.6.4） |

---

## 10. 关联文档

| 文档 | 关系 |
|------|------|
| 场景⑩（[⑩定时清理](../业务场景实战/⑩定时清理.md)） | 迁移来源（@Scheduled+锁 现状） |
| [实战案例·七件套拆解](实战案例——分布式协调机制七件套拆解.md) §3.7 | XXL-Job 概览（本篇展开） |
| [分布式并发全景](../服务架构/分布式并发全景——有序性原子性互斥.md) §5 | 调度/有序性的上层框架 |
| [T7·Redisson](T7·Redisson分布式锁.md) | 不上调度平台时的锁方案 |
| [`XxlJobConfig`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/XxlJobConfig.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/XxlJobConfig.java)）/ [`XxlJobDemoHandler`](../../backend/src/main/java/com/aiexplorer/researchagent/demo/XxlJobDemoHandler.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/demo/XxlJobDemoHandler.java)） | 本课 demo 代码 |
| [`RetentionCleanupJob`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java)） | 迁移对象（现状） |

---

## 11. 一句话总结

```text
XXL-Job = 调度中心（平台定闹钟+派单+重试+告警+日志）+ 执行器（应用内嵌收派单跑 @XxlJob）；
中心派单让调度互斥天然成立（不用再自己写锁），分片广播让大任务多实例并行；
但调度互斥 ≠ 永不重复——handler 内幂等照写；
配置必放 yml 顶层（xxl.job.* 不是 spring.* 前缀）——放错位置 = 执行器静默消失；
引入信号：任务多/要告警/要补跑/要分片/出过事故——两条以上就上平台，当前项目留在 @Scheduled+锁。
```
