# M3 · Kafka 基础架构

> **一课目标**：把 Kafka 的骨架拆开——Topic/Partition/Consumer Group/offset/ISR/Rebalance 到底各管什么，
> 并用本项目真实运行数据验证（2026-11-18 实测，容器 `research-kafka`）。
> **前置**：M1（概念对照表）｜**后续**：M4 生产级（可靠性三问）
> **配套**：[附录·Kafka核心概念图文总览](附录·Kafka核心概念图文总览.md)（16 个概念一图 + 详解，随时查）

---

## 涉及代码与配置

| 位置 | 本课用途 |
|---|---|
| [`KafkaTopicConfiguration.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/KafkaTopicConfiguration.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/KafkaTopicConfiguration.java) | Topic 声明：3 分区 / 1 副本 |
| [`TaskExecutionProducer.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionProducer.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionProducer.java) | key=taskId（分区路由的源头） |
| [`TaskExecutionConsumer.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionConsumer.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionConsumer.java) | @KafkaListener（消费组的一员） |
| [`application.yml`](../../backend/src/main/resources/application.yml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml) | group-id / auto-offset-reset / max.poll.interval.ms |
| [`docker/docker-compose.yml`](../../backend/docker/docker-compose.yml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/docker/docker-compose.yml) | KRaft 单节点 + 双监听器（9092/9093/9094） |

---

## 一、一张架构图（先把角色摆清）

```text
                        ┌───────────── Kafka 集群（本机 docker 单节点 KRaft） ─────────────┐
                        │                                                                   │
  Producer ──send()──→  │  Topic: research-task-execution                                   │
  （TaskExecutionProducer）│    ├── Partition 0 ── 只追加日志（segment 文件）──┐              │
                        │    ├── Partition 1 ── 只追加日志 ────────────────┤ 副本(Replica)  │
                        │    └── Partition 2 ── 只追加日志 ────────────────┘   = 1（单节点）  │
                        │                                                                   │
                        │  Consumer Group: research-agent-backend                           │
                        │    └── 消费者实例（本项目 1 个，吃 3 个分区）                       │
                        └───────────────────────────────────────────────────────────────────┘
                                        ↑ offset 提交到内置主题 __consumer_offsets
```

**角色一句话**：

```text
Broker       = 服务器进程（存数据、管分区、处理请求）——本项目 1 个（KRaft：broker+controller 合一）
Topic        = 逻辑分类（业务维度：research-task-execution）
Partition    = 物理存储单元（只追加日志；分区内有序；并发单位）
Replica      = 分区副本（本项目 1；生产 >= 3）——M4 的 ISR 主题
Producer     = 写入方（决定消息进哪个分区：key 哈希）
Consumer     = 读取方（以消费组为单位，组内分摊分区）
```

## 二、Partition：只追加日志（Kafka 的心脏）

```text
Partition 在磁盘上 = 一串 segment 文件（追加写，永不修改历史）：
  /var/lib/kafka/data/research-task-execution-0/00000000000000000000.log     ← 消息体
                                         00000000000000000000.index   ← 稀疏索引（offset → 文件位置）
                                         00000000000000000000.timeindex ← 时间索引

offset：分区内单调递增的编号（0,1,2,...）——消费者用它记录"读到哪了"
```

**本项目实测（`kafka-topics.sh --describe`）**：

```text
Topic: research-task-execution  TopicId: aA7FKKtkR_a_VTg_kq_EDw  PartitionCount: 3  ReplicationFactor: 1
  Partition: 0  Leader: 1  Replicas: 1  Isr: 1
  Partition: 1  Leader: 1  Replicas: 1  Isr: 1
  Partition: 2  Leader: 1  Replicas: 1  Isr: 1
```

**逐行读**：

```text
① PartitionCount=3 → 最多 3 个消费者并行（并发上限）；也是 Topic 声明里 partitions(3) 的结果
② ReplicationFactor=1 → 每分区只有 1 份（单节点；broker 挂 = 数据可能丢）→ 生产 >= 3
③ Leader=1 / Replicas=1 / Isr=1 → 单节点下三者都是同一个 broker（id=1）
④ Isr（In-Sync Replicas）= 与 leader 保持同步的副本集合 → M4 决定"acks=all 时要不要等"
```

**为什么分区数决定了并发上限？**

```text
规则：同一个消费者组内，一个分区【只能】被一个消费者消费
  → 3 分区 → 最多 3 个消费者实例并行（第 4 个会闲着）
  → 想扩并发：加分区（注意：加分区会改变 key 的哈希落点→历史消息顺序可能受影响）
```

## 三、有序性：Kafka 只保证【分区内】有序

```text
生产侧：key 哈希 → 固定分区
  key = taskId → hash(taskId) % 3 → 同一任务的所有消息 → 同一分区 → 分区内串行

消费侧：分区只由一个消费者消费（组内）
  → 同一分区的消息被单线程串行处理 → 天然有序

结论：全局有序做不到（不同分区并行）；需要的"业务有序"靠 key 收窄到分区内
```

**项目对照（`TaskExecutionProducer.publish`）**：

```java
kafkaTemplate.send(TOPIC, payload, payload); // send(topic, key=taskId, value=taskId)
```

```text
key=taskId 的三个好处：
① 同任务消息同分区 → 有序（"开始执行"不会晚于"取消执行"）
② 同任务串行消费 → 天然防并发重复执行（M1 7.6 的分布式锁是第二道保险）
③ 不同任务分散到不同分区 → 并发度不受影响
```

## 四、Consumer Group 与 offset（本项目真实数据）

### 4.1 实测：一个消费者实例吃下 3 个分区

`kafka-consumer-groups.sh --describe --group research-agent-backend`（2026-11-18）：

```text
GROUP                  TOPIC                   PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG   CONSUMER-ID
research-agent-backend research-task-execution 2          1               1               0     consumer-research-agent-backend-1-ab874eea...
research-agent-backend research-task-execution 0          -               0               -     consumer-research-agent-backend-1-ab874eea...
research-agent-backend research-task-execution 1          -               0               -     consumer-research-agent-backend-1-ab874eea...
```

**逐列读（这是最该会读的一张表）**：

```text
CURRENT-OFFSET  = 该分区消费者已提交的消费位置（"-" = 还没提交过 offset）
LOG-END-OFFSET  = 该分区最新消息的下一条位置（= 消息总数）
LAG             = LOG-END-OFFSET - CURRENT-OFFSET = 【积压条数】
                  · 分区2：1-1=0 → 无积压
                  · 分区0/1：有 offset 但从未消费 → "-"（该分区没来过消息或没处理过）
CONSUMER-ID     = 哪个消费者实例在吃这个分区（这里同一个实例吃 3 个分区 → 组内分摊）
```

```text
★ 这张表就是运维排障第一现场：
    LAG 持续变大 = 消费跟不上（扩容消费者 / 优化处理耗时 / 加分区）
    CONSUMER-ID 为 "-" 且 LAG>0 = 没有消费者在工作（组内无实例）
```

### 4.2 组内分摊 vs 组间广播

```text
同一个 group-id：分区在组内【分摊】——扩实例 = 扩并发（上限=分区数）
不同 group-id：各自独立消费【全量】——这就是"广播"（M1 概念表说过）
  → 想加一个"审计消费者"？换个 group-id 订阅同一 topic 即可，生产者零改动
```

### 4.3 offset 存在哪

```text
内置主题 __consumer_offsets（本项目实测存在于主题列表里）：
  每个 (group, topic, partition) 一条记录 → 消费者重启后从这里恢复位置
  这就是"消费位置可在服务端持久化"的含义
```

### 4.4 auto-offset-reset（项目配置）

```yaml
spring:
  kafka:
    consumer:
      group-id: research-agent-backend
      auto-offset-reset: earliest   # 新消费组从【最早】消息开始读
```

```text
earliest：新组从头读（学习环境友好：能看到历史消息 = 可以演示"重放"）
latest  ：新组从最新读（生产默认更常见：不关心历史）
注意：只对"新组/无 offset 记录"生效；已有 offset 的组按 offset 继续
```

## 五、Rebalance（重平衡）：Kafka 最需要注意的机制

```text
Rebalance = 重新分配"哪个消费者吃哪些分区"
```

**触发条件**：

```text
① 消费者加入/离开（扩缩容、实例重启）
② 心跳超时（session.timeout.ms，默认 45s）
③ ★ 处理时间超过 max.poll.interval.ms（默认 5 分钟）→ 被踢出组 → 重平衡
```

**代价**：

```text
· STW：重平衡期间【整组暂停消费】（秒级到分钟级，取决于分区数/消费者数）
· 重复消费风险：分区易主后，新消费者从"上次提交的 offset"继续 → 未提交的消息重投
  → 所以消费必须幂等（M1 7.6）
```

**项目对照（本项目为什么调大）**：

```yaml
spring:
  kafka:
    consumer:
      properties:
        max.poll.interval.ms: 3600000   # 1 小时（默认 5 分钟）
```

```text
研究任务可能跑几分钟 → 默认 5 分钟会让 Kafka 误判"消费者死亡"
→ 踢出组 → 重平衡 → 消息重投 → 重复执行（幂等守卫兜底，但本可避免）
→ 这是"长任务 + Kafka"的必修配置（M4 继续讲）
```

## 六、日志段与保留：为什么 Kafka 能"重放"

```text
① 消息按 segment 文件追加（默认 1GB 一个），旧 segment 按策略删除
② 保留策略：retention.ms（时间，默认 7 天）/ retention.bytes（大小）
③ 【关键】消息被消费 ≠ 被删除——offset 只是"读到哪了"的标记
   → 重置 offset 就能重读历史 → 这就是"可回放"（M1 概念表的核心差异）
④ 压缩（compaction）：按 key 只保留最新值（适合"状态快照"类主题，如变更日志）
```

```text
对比 RabbitMQ：消费 ACK 后消息删除 → 没有"重读历史"（要重放只能重新投递）
```

## 七、Kafka vs RabbitMQ：架构根本差异（一表收口）

| 维度 | Kafka | RabbitMQ |
|---|---|---|
| 存储模型 | **分区 = 只追加日志**（保留 + 重放） | **队列**（ACK 后删除） |
| 消费模型 | **Pull**（消费者主动拉，按 offset） | **Push**（broker 推给消费者） |
| 有序单位 | 分区（key 哈希收窄） | 队列（单消费者才严格） |
| 并发单位 | 分区数（组内分摊） | 队列 + 消费者数 |
| 扩展方式 | 加分区 → 加消费者 | 加队列 → 加消费者 |
| 消费位置 | offset 存 `__consumer_offsets` | 无 offset（ACK 即删） |
| 重平衡 | 有（Rebalance，STW） | 无（消费者增减即分配） |
| 路由能力 | 弱（Topic/分区键） | 强（4 种 Exchange + DLX + TTL） |

## 八、实操命令（本项目 docker 环境，可直接复制）

```bash
# 进 Kafka 容器（compose 服务名 kafka / 容器名 research-kafka）
docker exec -it research-kafka bash

# ① 主题列表
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
# → __consumer_offsets
#   research-task-execution

# ② 主题详情（分区/副本/ISR）
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic research-task-execution
# → PartitionCount: 3 / ReplicationFactor: 1 / Isr: 1

# ③ 消费组列表 + 详情（看 LAG）
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group research-agent-backend

# ④ 手动生产一条消息（实验：观察 offset/LAG 变化）
/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic research-task-execution
# 输入一行内容回车（注意：应用消费者会在 10ms 守卫处拦下无效 taskId）

# ⑤ 从最早重放消费（观察"可回放"）
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic research-task-execution --from-beginning --max-messages 5
```

```powershell
# 宿主机一行式（不进容器）
docker exec research-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group research-agent-backend
```

## 八点五、消费循环与积压：pull 模型与长任务配置陷阱（2026-11-18 学员追问）

### 先纠正模型：Kafka 不是"ACK 后投下一条"

```text
RabbitMQ（push 模型）：
  broker 推一条 → 消费者处理 → ACK → broker 再推下一条

Kafka（pull 模型）：
  while (true) {
      records = consumer.poll();        // ① 主动拉一批（max.poll.records，默认 500）
      for (r : records) 处理(r);         // ② 逐条处理（本项目：一条任务几分钟）
      consumer.commit(offset);          // ③ 提交"读到哪"（不是删除消息）
  }

三点必须转过来：
  ① 拉（pull）不是推（push）
  ② 提交 offset ≠ 删除消息（按 retention 保留，默认 7 天）
  ③ 一批不是一条（默认 500 条一批，处理完一批提交一次位置）
```

### "消费跟不上"的完整链路

```text
LAG = LOG-END-OFFSET - CURRENT-OFFSET（积压条数）

原因：
  ① 单条处理慢（本项目一条任务几分钟）→ 优化/拆分
  ② 消费者实例少 → 扩实例（上限 = 分区数）
  ③ 分区少 → 加分区（会改 key 落点，慎）

后果链：
  LAG 涨 → ① 延迟变大（任务排队很久才执行）
          → ② 磁盘涨（segment 持续增长）
          → ③ 超过 retention → 消息删除 = 真的丢了
          → ④ 监控告警（消费延迟 SLA）
```

### "消费者停了"会怎样

```text
消费者挂/重启 → 分区无人消费 → LAG 涨（消息还在，没丢）
消费者回来 → 从 __consumer_offsets 的上次 offset 继续：
  · 已提交部分：不重复
  · 未提交部分：重投 → 可能重复执行（at-least-once → 幂等守卫兜底）
```

### ★ 本项目的配置陷阱（当前没暴露，积压就会出现）

```text
现状：max.poll.interval.ms=1h（已配）
      max.poll.records=500（默认，未配）★
      ack-mode=BATCH（默认，未配）★

推演：积压 500 条 × 每条 3 分钟 = 25 小时 >> max.poll.interval.ms(1h)
  → 处理约 20 条后被判定死亡 → Rebalance → 500 条重投 → 再拉 500 → 循环
  → 永远处理不完 + 无限重复

正确配置（长任务）：
  spring.kafka.listener.ack-mode: record       # 每条提交
  spring.kafka.consumer.max-poll-records: 1    # 一次一条
  spring.kafka.consumer.properties.max.poll.interval.ms: 3600000
  → 一批 = 一条 = 最多 3 分钟 << 1 小时 ✓ 安全
  → M4 生产级统一修复并实测验证
```
## 八点六、术语精讲：LAG / retention / SLA / segment（2026-11-18 学员追问）

> 每个词：字面 → 含义 → 类比 → 项目里的样子。速查版见 [术语表](术语表.md)。

### LAG（滞后量 / 积压条数）

```text
字面：lag = 落后、滞后
含义：消费者落后于生产者多少条消息没处理
公式：LAG = LOG-END-OFFSET（最新位置）− CURRENT-OFFSET（已消费位置）
```

**类比：食堂打饭**

```text
LOG-END-OFFSET = 锅里已经做好、摆出来的菜的总量（最新到第 100 份）
CURRENT-OFFSET = 你盘里已经打到的（你吃到第 30 份）
LAG            = 100 - 30 = 70 —— 还有 70 份你落后没打
```

**项目里的真实样子**（M3 第四节那张 describe 表）：

```text
PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
2          1               1               0     ← 吃完了，没落后
0          -               0               -     ← 从没打过（"-" = 没提交过位置）
```

```text
★ LAG 是消费者健康的【唯一核心指标】：
   LAG 持续涨 → 消费跟不上（人或机器太慢）
   LAG 涨且 CONSUMER-ID 为 "-" → 根本没有消费者在工作
```

### retention（保留期 / 保留策略）

```text
字面：retention = 保留、保存（记忆里的 retention 就是"记住多久"）
含义：Kafka 把消息在磁盘上保留多久，超过就删除——【与是否被消费无关】
配置：retention.ms（按时间，默认 7 天）/ retention.bytes（按总大小）
```

**类比：监控录像**

```text
监控录像保留 7 天：7 天内的随时能回看（重放），第 8 天的自动覆盖删除
→ 你"看没看过"这段录像，不影响它什么时候被删
```

**和消费的关系（最容易误解的点）**：

```text
❌ 错：消费完的消息就删了
✅ 对：消费只是移动"书签"（offset），消息本身按 retention 到期才删
→ 所以 7 天内你随时可以 --from-beginning 重放（第八节那个命令）
→ 超过 7 天没消费 → 消息被删 → 【真的丢了】（这就是"消费跟不上"的终极代价）
```

### SLA（服务等级协议）

```text
字面：Service Level Agreement = 服务等级【协议】
含义：对外承诺的服务质量指标（做不到要赔/被投诉）
常见指标：可用性 99.9%（一年停机 ≤ 8.76 小时）、P99 延迟、消息投递延迟
```

**类比：外卖"30 分钟必达"**

```text
平台承诺 30 分钟送达（SLA）——超时赔付
商家怎么做到？靠后台指标（类似 LAG 这种）来监控和预警
```

**在 MQ 语境里**：

```text
"消费延迟 SLA = 消息从生产到被处理 ≤ 60 秒"
  ↓
LAG 是【手段指标】（过程）：LAG 涨了
SLA 是【结果承诺】（对用户）：消息延迟超 60 秒 → 违约
→ 两者关系：LAG 持续涨 ⇒ 延迟变大 ⇒ 迟早突破 SLA ⇒ 告警
```

### segment（日志段 / 分段文件）

```text
字面：segment = 段、片段（把长的切成段）
含义：一个 Partition 在磁盘上【不是一个巨大文件】，而是切成多个段文件
      默认每段 1GB，写满就切换新段
文件三件套（同名前缀，起始 offset 命名）：
  00000000000000000000.log       ← 消息体（追加写）
  00000000000000000000.index     ← 稀疏索引（offset → 文件内位置）
  00000000000000000000.timeindex ← 时间索引（按时间找 offset）
```

**类比：厚书分册**

```text
一本 1000 页的书不好翻 → 分成 10 册，每册 100 页
找内容：先查目录（.index 稀疏索引）定位到第几册第几页 → 再翻正文（.log）
```

**为什么要分段（三个实际好处）**：

```text
① 删除旧数据快：retention 到期 → 直接删整个段文件（不用在大文件里挖洞）
② 索引小：稀疏索引（每 4KB 记一条），不必记录每条消息位置
③ 顺序写友好：追加写 + 顺序读（磁盘最擅长的模式，这也是 Kafka 高吞吐的原因之一）
```

**项目里的样子**：

```text
容器内：/var/lib/kafka/data/research-task-execution-0/
        ├── 00000000000000000000.log
        ├── 00000000000000000000.index
        └── 00000000000000000000.timeindex
（本项目消息量小，都还在第 0 段里）
```

### 四个词怎么协作

```text
消息写进 segment（分段日志）
   → 消费者读 → 提交 offset（书签）
   → 落后量 = LAG（健康指标）
   → 消息到期 = retention 删除（不看是否消费）
   → LAG 涨到延迟超承诺 = SLA 违约（告警）
```
## 八点七、Kafka 配置全景（Spring Boot 侧，2026-11-18 学员问）

> 配置分三层：① Spring Boot 封装（`spring.kafka.consumer.group-id` 这类短名）
> ② 原生客户端透传（`spring.kafka.consumer.properties.max.poll.interval.ms`——Spring 没封装短名的走这里）
> ③ Broker 侧（docker-compose 的 `KAFKA_*` 环境变量 / server.properties）
> **判断规则：Spring 文档查不到短名 → 写 `properties.原始参数名`**

### A. 连接

| 配置 | 默认 | 说明 |
|---|---|---|
| `spring.kafka.bootstrap-servers` | localhost:9092 | Broker 地址（集群逗号分隔） |

### B. 生产者（M4 重点）

| 配置 | 默认 | 说明 |
|---|---|---|
| `producer.acks` | all | 可靠性：0 不管 / 1 leader 落盘 / all ISR 全部 |
| `producer.retries` | 2147483647 | 重试次数（配合 delivery.timeout） |
| `producer.retry.backoff.ms` | 100 | 重试间隔 |
| `producer.key-serializer` / `value-serializer` | — | 序列化器 |
| `producer.linger.ms` | 0 | 吞吐：攒批等待（0 立即发） |
| `producer.batch-size` | 16384 | 批大小 |
| `producer.compression-type` | none | 压缩（none/gzip/snappy/lz4/zstd） |
| `producer.properties.enable.idempotence` | true | 幂等生产者（不重复） |
| `producer.properties.max.in.flight.requests.per.connection` | 5 | >1 且未开幂等会乱序 |
| `producer.transaction-id-prefix` | — | 事务（精确一次） |
| `producer.properties.delivery.timeout.ms` | 120000 | 发送总超时 |

### C. 消费者（M4/M6 重点）

| 配置 | 默认 | 说明 |
|---|---|---|
| `consumer.group-id` | — | 消费组 |
| `consumer.auto-offset-reset` | latest | 新组起始：earliest/latest/none |
| `consumer.enable-auto-commit` | true | 自动提交 offset（建议 false + 手动） |
| `consumer.auto-commit-interval` | 5000 | 自动提交间隔 |
| `consumer.key-deserializer` / `value-deserializer` | — | 反序列化器 |
| `consumer.max-poll-records` | **500** | 一次拉多少条 ★本项目陷阱 |
| `consumer.fetch-min-size` / `fetch-max-wait` | 1 / 500ms | 拉取批量优化 |
| `consumer.properties.max.poll.interval.ms` | 300000 | 两次 poll 最大间隔（长任务命门） |
| `consumer.properties.session.timeout.ms` | 45000 | 心跳超时（判活） |
| `consumer.properties.heartbeat.interval.ms` | 3000 | 心跳间隔 |
| `consumer.properties.partition.assignment.strategy` | Range | 分区分配策略 |
| `consumer.properties.isolation.level` | read_uncommitted | 事务消息读隔离 |

### D. 监听容器（@KafkaListener 容器，M4 重点）

| 配置 | 默认 | 说明 |
|---|---|---|
| `listener.ack-mode` | BATCH | 提交时机：RECORD/BATCH/TIME/MANUAL ★ |
| `listener.concurrency` | 1 | 容器并发线程数（上限=分区数） |
| `listener.type` | single | single/batch |
| `listener.missing-topics-fatal` | false | 主题不存在是否启动失败 |
| `listener.poll-timeout` | 5000 | poll 超时 |

### E. 管理

| 配置 | 默认 | 说明 |
|---|---|---|
| `admin.auto-create` | false | 启动时自动建主题 |
| `admin.fail-fast` | false | 管理操作失败是否让应用启动失败 |

### 四种方案组合（按场景）

| 场景 | 关键组合 |
|---|---|
| **长任务**（本项目） | `max-poll-records: 1` + `ack-mode: record` + `max.poll.interval.ms: 1h` |
| **高吞吐** | `linger.ms: 5~100` + `batch-size: 64KB` + `compression-type: lz4` |
| **最高可靠** | `acks: all` + `enable.idempotence: true` + `isolation.level: read_committed` |
| **最低延迟** | `linger.ms: 0` + `acks: 1` + `fetch-max-wait: 0` |

### 课程分工（别在本课死磕）

```text
M3（本课） 已讲：bootstrap-servers / group-id / auto-offset-reset / max.poll.interval.ms
M4         专讲：acks + ISR + retries + 幂等生产者 + 事务 + 顺序 + 积压治理
           → 并修掉本项目 max-poll-records/ack-mode 陷阱 + 造积压实测
M6         专讲：丢/重/序/积压四大问题（配置方案 + 实验）
M7         只讲 Broker 层对比（部署/运维成本）
```
## 九、自测题（答案在下方）

```text
Q1 本项目 research-task-execution 有 3 个分区——最多几个消费者实例并行？第 4 个会怎样？
Q2 describe 表里 CURRENT-OFFSET=1、LOG-END-OFFSET=3 时 LAG 是多少？意味着什么？
Q3 为什么"消息消费后不删除"能让 Kafka 支持重放？RabbitMQ 为什么不行？
Q4 max.poll.interval.ms 默认 5 分钟，为什么长任务必须调大？不调的后果链是什么？
Q5 同一个 key（taskId）的消息为什么一定有序？换随机 key 会破坏什么？
```

### 答案

```text
Q1 最多 3 个（一个分区只能被同组一个消费者消费）；第 4 个会闲着（分配不到分区）
   → 想用满第 4 个必须先加分区

Q2 LAG = 3 - 1 = 2 → 有 2 条消息积压未被消费（消费跟不上或消费者停了）

Q3 Kafka 的 offset 只是"读取位置"标记，消息按 retention 保留 → 重置 offset 就能重读；
   RabbitMQ 消费 ACK 后消息删除 → 没有可重读的"历史"，要重放只能重新投递

Q4 处理时间 > 5 分钟 → Kafka 认为消费者死亡（心跳虽在，但 poll 停滞）
   → 踢出消费组 → 触发 Rebalance（整组 STW）→ 分区易主 → 未提交消息重投 → 重复执行
   → 项目把 max.poll.interval.ms 调到 1 小时，配合"至少一次 + 幂等守卫"兜底

Q5 同一 key 哈希到同一分区，分区内只追加且单消费者串行 → 顺序天然保证；
   随机 key 会把同一任务的消息散到不同分区并发处理 → "开始/取消"可能乱序 → 状态机被破坏
```

## 十、一句话

```text
Kafka = 分区化的只追加日志：
  key 决定分区（顺序），分区决定并发（消费者上限），offset 决定读到哪里（可重放），
  消费组决定谁读哪个分区（Rebalance 是它的代价）。
看 LAG、看 ISR、看 Rebalance —— 这就是 Kafka 运维的三块仪表盘。
```
