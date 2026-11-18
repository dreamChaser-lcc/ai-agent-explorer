# M4 · Kafka 生产级（可靠性三问：不丢 / 不重 / 有序）

> **一课目标**：把"能用"升级为"敢上生产"——回答三问：**消息会不会丢？会不会重？顺序保不保？**
> **前置**：M1（概念）/ M3（分区·offset·ISR·Rebalance）
> **本课动作**：修掉本项目 `max-poll-records` / `ack-mode` 的配置陷阱（M3 八点五 发现的），并实测。
> **后续**：M6 四大可靠性问题专题（把三问 + 积压拆成四个实验）

---

## 涉及代码与配置

| 位置 | 本课用途 |
|---|---|
| [`application.yml`](../../backend/src/main/resources/application.yml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml) | 本课要改的 kafka 段（ack-mode / max-poll-records / max.poll.interval.ms） |
| [`TaskExecutionProducer.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionProducer.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionProducer.java) | 生产端：key=taskId（顺序）+ 未显式配置 acks（走默认 all） |
| [`TaskExecutionConsumer.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionConsumer.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionConsumer.java) | 消费端：@KafkaListener（幂等守卫生效位置） |
| [`docker/docker-compose.yml`](../../backend/docker/docker-compose.yml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/docker/docker-compose.yml) | Broker 侧：单节点 KRaft（replicas=1 / min.insync 默认 1） |

---

## 一、可靠性三问（本课主线）

```text
问 1（不丢）：生产者发了、broker 存了、消费者处理了——哪一步可能丢？怎么防？
问 2（不重）：网络重试、Rebalance、重复投递——重复从哪来？怎么兜？
问 3（有序）：并发 + 重试会让消息乱序——什么条件下能保序？

★ 一句话：可靠性不是"某个配置"，而是【生产端 + Broker 端 + 消费端】三处同时配合。
```

## 二、问 1：不丢消息（三处都要管）

### 2.1 生产端：acks + retries + 超时

| `acks` | 含义 | 丢消息风险 | 延迟 |
|---|---|---|---|
| `0` | 发出去不管（不等待任何确认） | **最高**（网络一抖就丢） | 最低 |
| `1` | 等 **leader** 落盘 | **中**（leader 挂了且 follower 没同步到） | 中 |
| `all`（= -1） | 等 **ISR 全部**落盘 | **最低**（配合 min.insync.replicas） | 最高 |

```yaml
# 生产端"不丢"三件套（Kafka 3.x 默认值已较安全，显式写出来便于审查）
spring:
  kafka:
    producer:
      acks: all                       # ★ 等 ISR 全部确认（Spring Boot 默认就是 all）
      retries: 2147483647             # 重试到超时为止（默认）
      properties:
        delivery.timeout.ms: 120000   # 发送总超时（重试 + 等待的总预算）
        enable.idempotence: true      # 幂等（Kafka 3.x 默认 true）
```

**逐点讲解**：

```text
① acks=all 是"不丢"的第一道门：ISR 全部写入才算成功
   —— 但单节点（replicas=1）时 ISR 只有自己 → 等价于 acks=1（M3 实测 Isr: 1）
   → 本项目是【开发单节点】，生产必须 replicas>=3
② retries 大 + delivery.timeout 兜底：网络抖动自动重试，直到超时才失败（失败要告警）
③ enable.idempotence=true（默认）：重试不会造成重复（见第三节）
```

### 2.2 Broker 端：副本 + ISR + 禁止脏选举

```text
生产集群（>=3 broker）的三条硬要求：
  ① replication.factor >= 3        —— 每分区 3 副本
  ② min.insync.replicas = 2        —— acks=all 时至少 2 个副本确认才算成功
  ③ unclean.leader.election.enable = false  —— 不让落后太多的副本当 leader（宁不可用不丢数据）

本项目（开发单节点）：
  replicas=1 / min.insync.replicas=1（默认）/ KRaft 单节点
  → acks=all 只等自己 → 【开发环境可接受，生产禁止】
```

**ISR / HW / LEO 三个词（M3 只提了 ISR，这里补全）**：

```text
LEO（Log End Offset）  = 每个副本日志末尾的位置
HW （High Watermark）  = 【所有 ISR 都同步到】的位置
                        → 消费者只能读到 HW 之前的消息（保证读到的都是"已复制"的）
ISR（In-Sync Replicas）= 与 leader 保持同步的副本集合（落后超过 replica.lag.time.max.ms 会被踢出）

acks=all 的真实含义：等所有 ISR 都追到这条消息（而不是"等所有副本"——ISR 外的不等）
```

### 2.3 消费端：先处理，后提交（不吞异常）

```yaml
spring:
  kafka:
    listener:
      ack-mode: record        # ★ 每条处理成功才提交 offset（本课要改的项）
    consumer:
      enable-auto-commit: false   # 关闭自动提交（配合 ack-mode 生效）
```

```text
① 自动提交（默认 true）：poll 到就提交 → 处理失败也提交 → 【消息丢了】
② 手动/a ck-mode 提交：处理成功才提交 → 失败不提交 → 重投（不丢，但可能重复 → 幂等兜）
③ 绝不 catch 异常后静默 return（那等于"假装处理成功"→ 丢消息）
```

## 三、问 2：不重复（幂等生产者 + 消费幂等）

### 3.1 重复从哪来（三个源头）

```text
① 生产端重试：发了没收到 ack → 重发 → broker 可能收到两条
② Rebalance：分区易主 → 新消费者从上次提交的 offset 重读 → 未提交部分重投
③ 消费端处理成功但提交失败：offset 没提交 → 重投
```

### 3.2 生产端：幂等生产者（broker 侧去重）

```text
原理：Producer 启动时拿一个 PID（Producer ID），每条消息带 (PID, 分区, 序列号)
     broker 对同一 PID+分区的序列号去重 → 重试的消息不会写两遍
开启：enable.idempotence=true（Kafka 3.x 默认）
约束：要求 acks=all + retries>0 + max.in.flight<=5（Kafka 会自动校验/调整）
```

### 3.3 更进一步：事务（跨分区原子写）

```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: tx-    # 开启事务（如 consume-transform-produce 场景）
    consumer:
      properties:
        isolation.level: read_committed   # 只读已提交的事务消息（否则读到中间态）
```

```text
适用：流处理（读 A 写 B 原子）、精确一次要求高的链路
代价：延迟增加、吞吐下降、运维复杂 → 【本项目不需要】
```

### 3.4 消费端：幂等（本项目已有三层）

```text
① 分布式锁（场景⑨）：并发重复执行 → 拿不到锁跳过
② 状态守卫（shouldStop）：终态任务重投 → 直接跳过（场景⑥ 的 10ms 守卫）
③ 步骤守卫：已完成步骤 → 跳过（断点续跑）

★ 端到端"事实恰好一次"公式：
   acks=all + 幂等生产者（生产端不重）
 + 至少一次投递 + 消费幂等（消费端兜重）
 = 业务效果上恰好一次
```

## 四、问 3：有序（三个条件缺一不可）

```text
① 生产端：max.in.flight.requests.per.connection <= 5 且 enable.idempotence=true
   —— 否则重试可能让第 2 条先于第 1 条到达（乱序）
② 分区：同一 key 的消息必须进同一分区（本项目 key=taskId ✓）
③ 消费端：同一分区只被一个消费者串行处理（Kafka 天然保证）
```

```yaml
# 生产端保序配置（Kafka 3.x 默认幂等=true 时，in.flight 上限 5 内有序）
spring:
  kafka:
    producer:
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5   # 不要 >5（大于 5 幂等也会失效）
```

**判断口诀**：

```text
"要不要全局有序？" → 不需要（代价太高：只能 1 分区 / 1 消费者）
"要不要业务有序？" → 要 → key 收窄到分区内（本项目 key=taskId）
```

## 五、积压治理（LAG 实战）

```text
判断：kafka-consumer-groups.sh --describe 看 LAG（M3 第四节读法）
      或监控平台（LAG 曲线 / 消费延迟）
```

**四种手段（按代价排序）**：

| 手段 | 做法 | 代价 |
|---|---|---|
| ① 优化处理 | 减少每条耗时（本项目：拆步骤/并行子任务） | 改代码 |
| ② 扩消费者 | 实例数 ≤ 分区数（3 分区最多 3 个） | 需加分区才能再扩 |
| ③ 加分区 | `kafka-topics.sh --alter --partitions 6` | **历史 key 落点改变 → 顺序受影响** |
| ④ 降级/丢弃 | 非核心消息采样消费 / 死信 + 人工 | 丢数据（最后手段） |

**应急套路**：

```text
突发积压 → ① 先扩消费者实例（到分区数上限）
          → ② 同时临时降低单条耗时（跳过非关键步骤）
          → ③ 都不够 → 加分区（接受顺序风险）+ 长期优化
```

## 六、本课动作：修掉本项目的配置陷阱（M3 八点五 发现）

### 6.1 问题回顾

```text
现状：max.poll.interval.ms=1h（已配）
      max.poll.records=500（默认，未配）★
      ack-mode=BATCH（默认，未配）★
推演：积压 500 条 × 每条 3 分钟 = 25h >> 1h
     → 处理约 20 条被判定死亡 → Rebalance → 500 条重投 → 循环
```

### 6.2 修复（长任务方案）

`application.yml`（kafka 段）

```yaml
spring:
  kafka:
    listener:
      # ★ 长任务：每条处理成功就提交 offset（避免整批提交的窗口过大）
      ack-mode: record
    consumer:
      group-id: research-agent-backend
      auto-offset-reset: earliest
      # ★ 一次只拉一条：长任务场景避免"一批卡住 → 全批重投 → 重复执行"
      max-poll-records: 1
      properties:
        # ★ 单条消息最长处理预算（1 小时），必须 > 单条最长耗时
        max.poll.interval.ms: 3600000
```

**修复后的保障链**：

```text
一批 = 1 条 = 最多 3 分钟 << max.poll.interval.ms（1 小时）✓ 不会误判死亡
处理成功 → 立即提交 offset（record 模式）→ 最多重复 1 条（不是 500 条）
处理失败 → 不提交 → 重投 → 幂等守卫兜底 ✓
```

### 6.3 验证方法

```powershell
# ① 启动（docker 栈在跑）
.\start-dev-docker.ps1
# ② 看消费组 offset/LAG（每条消息后立即变化）
docker exec research-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group research-agent-backend
# ③ 灌 20 条消息造积压 → 观察 LAG 逐条下降（而不是整批一次性归零）
docker exec research-kafka bash -c "for i in $(seq 1 20); do echo 00000000-0000-0000-0000-0000000000$i; done | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic research-task-execution"
```

```text
观察点：
· 若 ack-mode=BATCH（改前）：20 条一起处理完才提交 → LAG 从 20 直接归 0
· 若 ack-mode=record（改后）：每处理一条提交一次 → LAG 一条条往下掉
· 无效 taskId 会在状态守卫/锁处被拦（日志能看到），但 offset 仍会提交（说明消费"成功"）
```

## 七、生产级检查清单（上线前逐项打勾）

```text
□ acks=all（生产端）
□ replication.factor >= 3 / min.insync.replicas = 2（Broker 端）
□ unclean.leader.election.enable = false
□ enable.idempotence = true（生产端去重）
□ 消费端：ack-mode=record（或 manual）+ enable-auto-commit=false
□ 消费幂等：锁/状态守卫/唯一索引（业务侧兜底）
□ 有序：key 设计 + in.flight<=5 + 幂等
□ 积压：LAG 监控 + 告警 + 扩容预案（消费者数 ≤ 分区数）
□ 死信：处理失败 N 次进 DLT（M6）
```

## 八、自测题（答案）

```text
Q1 acks=all 就保证不丢了吗？还差什么？
Q2 幂等生产者能解决"消费端重复"吗？
Q3 开启幂等后，max.in.flight 设 10 会怎样？
Q4 本项目单节点 replicas=1 时 acks=all 等于什么？有什么风险？
Q5 积压 10000 条、3 分区、1 个消费者——第一步该做什么？
```

### 答案

```text
Q1 不够。acks=all 只保证"ISR 全部写入"；还要：replication.factor>=3 + min.insync.replicas=2
   + unclean.leader.election=false（否则 ISR 缩到 1 时 acks=all 退化为 acks=1）；
   消费端还要关自动提交 + 不吞异常。

Q2 不能。幂等生产者只保证【生产端重试】不产生重复；
   消费端的重复（Rebalance 重投/提交失败）要靠消费幂等（锁/状态守卫/唯一索引）。

Q3 幂等的顺序保证被破坏：in.flight>5 时重试可能乱序（Kafka 会在开启幂等时把上限压到 5，
   或直接报错）。所以要么 in.flight<=5，要么单分区单消费者。

Q4 等价于 acks=1（ISR 只有自己）。风险：broker 磁盘坏 = 消息直接丢；
   leader 挂 = 无副本可切换（不可用）。生产必须 >=3 副本。

Q5 先判断瓶颈：① 看 LAG 曲线 + 单条处理耗时；
   ② 若消费者实例 < min(分区数, 需要) → 先扩实例（到 3 个，不要超过 3）；
   ③ 若实例已满 3 个 → 优化单条耗时，或加分区（接受 key 落点变化的风险）；
   ④ 应急可临时降级（跳过非关键步骤）。
```

## 九、一句话

```text
不丢 = 生产(acks=all) + Broker(replicas>=3/min.insync=2) + 消费(处理后才提交)
不重 = 生产(幂等生产者) + 消费(业务幂等)
有序 = key 收窄分区 + in.flight<=5 + 幂等 + 单分区串行
积压 = LAG 监控 + 扩消费者（≤分区数）→ 加分区 → 优化 → 降级
长任务的三件套：max-poll-records=1 / ack-mode=record / max.poll.interval.ms=1h
```
