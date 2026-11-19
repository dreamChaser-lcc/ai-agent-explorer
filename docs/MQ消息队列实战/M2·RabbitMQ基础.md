# M2 · RabbitMQ 基础（AMQP 模型 / Exchange / ACK / 死信）

> **一课目标**：理解 RabbitMQ 的"邮局"模型——Exchange 怎么路由、ACK 怎么确认、DLX 怎么兜底；
> 与 Kafka 的差异对照记忆。**本课只写代码与设计，环境待你本地 `docker run` 后再跑（第八节）**。
> **前置**：M1（概念对照表）/ M6（四大问题）｜**后续**：M5 的 spring-amqp 部分

---

## 一、AMQP 模型：比 Kafka 多一层"路由"

```text
Kafka 模型：Producer → Topic（Partition）→ Consumer Group
                  ↑ 没有路由层：key 哈希直接决定分区

RabbitMQ 模型：
  Producer → 【Exchange】─(Binding: routing key)→ 【Queue】 → Consumer
                ↑                    ↑
             路由规则              队列（真正的存储单元）

★ 关键差异：Kafka 的"收件人"是分区（哈希定死），RabbitMQ 的"收件人"是队列（由 Exchange 按规则找）
  → 这就是 RabbitMQ 路由能力的来源（灵活），也是 Kafka 简单高吞吐的原因（无路由开销）
```

**五个角色一句话**：

```text
Producer     发消息（发给 Exchange，不是发给 Queue！）
Exchange     路由器：按类型 + routing key 决定进哪些 Queue
Binding      Exchange 与 Queue 之间的"路由规则"（可带 routing key / 参数）
Queue        真正的存储单元（消息躺在这里等消费）
Consumer     从 Queue 取消息（可多个消费者分摊同一队列）
```

## 二、四种 Exchange（RabbitMQ 的核心卖点）

| 类型 | 路由规则 | 典型场景 |
|---|---|---|
| **direct** | routing key **完全匹配** binding key | 精确业务路由（order.create → 订单队列） |
| **fanout** | **广播**（忽略 routing key，发给所有绑定队列） | 事件通知（任务完成 → 通知/审计/统计） |
| **topic** | 模式匹配（`*` 一个单词 / `#` 多个单词） | 按业务域分级路由（`task.*.created`） |
| **headers** | 按消息头（headers）匹配 | 复杂属性路由（少用） |

```text
例子：topic 类型的匹配规则
  binding：task.execution.*
  命中：task.execution.started ✅ / task.execution.completed ✅
  不命中：task.notify.created ❌（中间段不匹配）
  binding：task.#
  命中：task.a / task.a.b / task.a.b.c ✅（# 匹配任意多段）
```

**默认 Exchange**：`""`（空名，直接按队列名路由——最简单但没路由能力）

## 三、Queue 与 Binding（声明时的三个属性）

```java
// 队列三属性
@Queue(
    name = "task.execution.queue",
    durable = "true",        // 持久化：broker 重启后队列还在（消息需 deliveryMode=2 才持久）
    exclusive = "false",     // 排他：只允许声明它的连接使用（通常 false）
    autoDelete = "false" )   // 自动删除：没有消费者时删除（临时队列用 true）

// Binding（routing key 由 Exchange 类型决定语义）
@QueueBinding(
    value = @Queue(name = "task.execution.queue", durable = "true"),
    exchange = @Exchange(name = "task.exchange", type = ExchangeTypes.DIRECT, durable = "true"),
    key = "task.execution")              // direct：精确匹配
```

**durable vs 消息持久化的坑**：

```text
durable=true        → 队列元数据持久化（broker 重启队列还在）
deliveryMode=2      → 消息本身持久化（broker 重启消息还在）
★ 两个都要设才真的"不丢"：只设队列持久化，消息仍是内存态
```

## 四、生产确认与消费 ACK

### 4.1 生产者确认（publisher confirms）

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated   # 确认回调（broker 收到消息后回调）
    publisher-returns: true              # 路由失败回调（消息没进任何队列时）
```

```java
// 确认回调（对应 Kafka 的 send().whenComplete）
rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
    if (!ack) {
        log.error("消息未到达 broker: {}", cause);
        // → 落 outbox / 重试 / 告警（同 M6 一致性方案）
    }
});
```

### 4.2 消费 ACK（对应 Kafka 的 commit offset）

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual   # none / auto / manual（推荐 manual 或 auto）
```

```java
@RabbitListener(queues = "task.execution.queue")
public void onTask(String taskId, Channel channel,
                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
    try {
        doWork(taskId);
        channel.basicAck(deliveryTag, false);          // ✅ 成功确认（broker 删消息）
    } catch (Exception e) {
        channel.basicNack(deliveryTag, false, false);  // ❌ 拒绝且不重回队列 → 进死信（见第五节）
        // basicNack(tag, multiple, requeue=true) → 重回队列（可能死循环，慎用）
    }
}
```

```text
★ 与 Kafka 的对应关系：
  RabbitMQ basicAck       ↔ Kafka commit offset
  RabbitMQ basicNack      ↔ Kafka 抛异常不提交
  差异：RabbitMQ 确认后【消息删除】；Kafka 提交只是移动书签（消息保留）——所以 Kafka 能重放
```

## 五、死信队列 DLX + TTL（本课重点，对应 Kafka 的 .DLT）

### 5.1 什么消息会进死信

```text
① 被拒绝（basicNack / basicReject 且 requeue=false）
② 过期（TTL 到期没人消费）
③ 队列满了（x-max-length 达到上限）
```

### 5.2 DLX 配置（队列声明时挂死信交换机）

```java
@Bean
public Queue taskQueue() {
    return QueueBuilder.durable("task.execution.queue")
            .withArgument("x-dead-letter-exchange", "task.dlx")        // ★ 死信交换机
            .withArgument("x-dead-letter-routing-key", "task.dead")    // ★ 死信路由键
            .withArgument("x-message-ttl", 60000)                      // ★ 消息 TTL（60 秒）
            .build();
}

@Bean
public Queue taskDeadQueue() { return QueueBuilder.durable("task.dead.queue").build(); }

@Bean
public DirectExchange taskDlx() { return new DirectExchange("task.dlx"); }

@Bean
public Binding taskDeadBinding() {
    return BindingBuilder.bind(taskDeadQueue()).to(taskDlx()).with("task.dead");
}
```

```text
消息流转：
  正常：Producer → task.exchange → task.execution.queue → Consumer（basicAck 删除）
  死信：消费失败/过期 → task.dlx → task.dead.queue（人工/脚本处理）
★ 与 Kafka 的 .DLT 完全同构：失败消息"出去"到独立位置，主链路继续跑（M5 已实测 Kafka 的 DLT）
```

### 5.3 TTL + DLX = 延迟队列（RabbitMQ 的招牌用法）

```text
需求：任务 30 分钟后自动取消（未确认的订单 30 分钟关单）
实现（RabbitMQ 独有、Kafka 做不到）：
  ① 建队列 order.delay.queue，x-message-ttl=1800000（30 分钟）
  ② 挂 x-dead-letter-exchange → order.exchange
  ③ 下单时发消息到 delay 队列（无人消费）
  ④ 30 分钟后消息过期 → 进死信 → 路由到 order.exchange → 正常消费者收到"取消"消息

Kafka 做延迟消息要绕：延时 topic + 定时扫描（或外部调度）——这就是 RabbitMQ 的场景优势
```

## 六、Spring AMQP 代码（M5 的 amqp 部分预览）

```java
// ① 发送（RabbitTemplate 对应 KafkaTemplate）
@Service
public class TaskProducer {
    private final RabbitTemplate rabbitTemplate;

    public TaskProducer(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }

    public void publish(UUID taskId) {
        rabbitTemplate.convertAndSend(
                "task.exchange",        // exchange
                "task.execution",       // routing key
                taskId.toString());     // payload
        log.info("[MQ] 已发送 taskId={}", taskId);
    }
}

// ② 消费（@RabbitListener 对应 @KafkaListener）
@Component
public class TaskConsumer {
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "task.execution.queue", durable = "true",
                    arguments = {
                        @Argument(name = "x-dead-letter-exchange", value = "task.dlx"),
                        @Argument(name = "x-dead-letter-routing-key", value = "task.dead")
                    }),
            exchange = @Exchange(name = "task.exchange", type = ExchangeTypes.DIRECT),
            key = "task.execution"))
    public void onTask(String taskId) { ... }
}
```

**与 Kafka 版本的对照表**：

| 维度 | spring-kafka | spring-amqp |
|---|---|---|
| 发送 | `KafkaTemplate.send(topic, key, value)` | `RabbitTemplate.convertAndSend(exchange, routingKey, payload)` |
| 消费 | `@KafkaListener(topics=...)` | `@RabbitListener(queues=...)` / `bindings=@QueueBinding(...)` |
| 声明目的地 | `NewTopic` Bean | `Queue`/`Exchange`/`Binding` Bean 或注解内声明 |
| 确认 | commit offset（自动/手动） | `basicAck` / `basicNack` |
| 失败兜底 | `.DLT` 主题 + DefaultErrorHandler | `x-dead-letter-exchange`（DLX） |
| 延迟消息 | 需绕（定时扫描） | **TTL + DLX 原生支持** |

## 七、环境准备（你本地自己跑，本课不装）

```bash
# 一条命令起 RabbitMQ（带管理界面）
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management

# 管理界面：http://localhost:15672  （guest / guest）
# 队列/交换机/绑定/消息数都能在这里看（比 Kafka 的命令行友好）
```

```yaml
# 跑 M5 amqp 部分时，应用侧配置（示例）
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    publisher-confirm-type: correlated
    listener:
      simple:
        acknowledge-mode: manual
```

## 八、自测题（答案）

```text
Q1 Producer 把消息发给 Exchange 还是 Queue？为什么？
Q2 fanout 与 direct 的区别？"任务完成通知三个系统"用哪个？
Q3 durable=true 就保证消息不丢吗？
Q4 死信（DLX）与 Kafka 的 .DLT 有什么共同点？
Q5 延迟 30 分钟关单，为什么 RabbitMQ 比 Kafka 合适？
```

### 答案

```text
Q1 Exchange。Queue 是存储单元，Exchange 是路由器——解耦"谁发"与"发给谁"，
   加消费者/改路由不用动生产者。
Q2 fanout 广播（忽略 key 发给所有绑定队列）；direct 精确匹配。
   通知三个系统 → fanout（或 topic 按域匹配）。
Q3 不够。durable 只管队列元数据；消息还要 deliveryMode=2（持久化），
   且确认机制（publisher confirms）要开，三者配合才真的不丢。
Q4 都是"失败消息离开主链路、进独立位置"：主链路不被毒消息卡死，失败可追溯可重放。
   RabbitMQ 叫 DLX（交换机 + 死信队列），Kafka 叫 .DLT（死信主题）。
Q5 TTL + DLX 原生支持延迟投递（消息在队列里躺 30 分钟后自动转死信→目标队列）；
   Kafka 没有延迟投递，要自己做"延时 topic + 定时扫描"。
```

## 九、一句话

```text
RabbitMQ = 会路由的邮局：Exchange（4 种）决定谁收，ACK 决定删不删，
           DLX 兜失败，TTL+DLX 做延迟。
与 Kafka 的分工：要吞吐/回放用 Kafka，要路由/延迟/低延迟用 RabbitMQ（M7 详讲）。
```
