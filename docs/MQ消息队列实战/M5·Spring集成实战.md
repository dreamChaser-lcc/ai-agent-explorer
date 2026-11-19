# M5 · Spring 集成实战（spring-kafka 为主 + spring-amqp 预览）

> **一课目标**：搞清"框架替你做了什么"——`@KafkaListener` 背后是容器工厂、并发线程、ack 模式、错误处理器；
> 能用配置和代码把消费端调成"生产级"。**本课 spring-kafka 部分完全可跑（项目已在用）；spring-amqp 待 M2 环境就绪后补。**
> **前置**：M3（分区/offset/消费组）/ M4（acks/幂等/有序）/ M6（四大问题）
> **说明**：M5 原排在阶段 2（依赖 M2 RabbitMQ），现解耦——先学 spring-kafka 部分（占本节 80%）。

---

## 涉及代码与配置

| 位置 | 本课用途 |
|---|---|
| [`TaskExecutionConsumer.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionConsumer.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionConsumer.java) | @KafkaListener 的唯一实例（逐行拆解它） |
| [`application.yml`](../../backend/src/main/resources/application.yml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml) | kafka 段（group-id / ack-mode / max-poll-records / max.poll.interval.ms） |
| [`TaskExecutionProducer.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionProducer.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskExecutionProducer.java) | KafkaTemplate（发送侧） |
| [`pom.xml`](../../backend/pom.xml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/pom.xml) | spring-kafka 依赖（Spring Boot starter） |

---

## 一、两套集成栈（先看全家福）

| 中间件 | Spring 集成库 | 核心注解 | 底层容器 | 本项目状态 |
|---|---|---|---|---|
| Kafka | **spring-kafka** | `@KafkaListener` | `KafkaListenerContainer` | ✅ 已在用（场景⑥） |
| RabbitMQ | **spring-amqp** | `@RabbitListener` | `SimpleMessageListenerContainer` | ⬜ 待 M2 环境 |

```text
共同套路（你已经在 Spring 里见过三次）：
  @Transactional / @Scheduled / @KafkaListener —— 都是"注解声明 + 容器扫描 + 反射调用"
  ★ 记住这句话：注解只是"申请书"，真正干活的是容器（线程池 + 循环）
```

## 二、@KafkaListener 的原理（申请书 + 外包工 第 4 次）

### 2.1 从注解到消费循环，发生了什么

```text
① 启动：KafkaListenerAnnotationBeanPostProcessor 扫描所有 @KafkaListener
② 注册：为每个方法创建 MessageListenerContainer（用容器工厂的配置）
③ 启动容器：每个容器按 concurrency 起 N 个消费者线程
④ 每个线程循环：poll() → 反序列化 → 反射调用你的方法 → （按 ack-mode）提交 offset
⑤ 方法抛异常：交给 errorHandler（默认：一直重试，直到 max.poll 超时——★ 坑）
```

```java
// 项目里的"申请书"（TaskExecutionConsumer）
@KafkaListener(topics = TaskExecutionProducer.TOPIC)
public void onTaskExecutionRequested(String taskIdText) { ... }
```

```text
它背后自动完成的：
· 用 application.yml 的 group-id 入组（research-agent-backend）
· 用 concurrency（默认 1）起 1 个消费线程
· 消息到了反序列化（StringDeserializer）→ 传 String 进来
· 方法正常返回 → 按 ack-mode=record 提交 offset
· 方法抛异常 → 不提交 → 重投（因为没配 errorHandler → 默认无限重试，见第四节）
```

### 2.2 方法能接收哪些参数（签名灵活性）

```java
// 常见签名形态
void a(String payload)                              // 只要消息体（项目用法）
void b(String payload, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition)  // 带元数据
void c(ConsumerRecord<String, String> record)       // 完整记录（offset/key/headers）
void d(String payload, Acknowledgment ack)          // 手动 ack（需 ack-mode=MANUAL）
```

### 2.3 返回值（自动回消息）

```java
@KafkaListener(topics = "in")
@SendTo("out")                      // 返回值自动发到 out topic
public String handle(String in) { return in.toUpperCase(); }
```

```text
★ @SendTo 的语义：用 KafkaTemplate 把返回值发到指定 topic（请求-响应模式）
本项目没用（纯异步单向），但面试常问
```

## 三、容器工厂：所有"生产级开关"都在这

```java
// 默认容器工厂由 Spring Boot 自动配置（kafkaListenerContainerFactory）
// 需要定制时手写（示意，本项目暂未需要）
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        ConsumerFactory<String, String> cf) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(cf);
    factory.setConcurrency(2);                                   // 并发消费线程数（≤分区数）
    factory.getContainerProperties().setAckMode(AckMode.RECORD); // 等价于 yml 的 ack-mode
    factory.setCommonErrorHandler(new DefaultErrorHandler(...)); // 错误处理（第四节）
    return factory;
}
```

**配置优先级**（从高到低）：

```text
代码（工厂 Bean） > yml 的 spring.kafka.listener.* > yml 的 spring.kafka.consumer.* > Kafka 默认
```

**关键参数（yml 已配的对照）**：

| 参数 | 位置 | 本项目值 | 作用 |
|---|---|---|---|
| `concurrency` | listener | 默认 1 | 消费线程数（≤分区数，超了浪费） |
| `ack-mode` | listener | **record** | 每条处理完提交 |
| `max-poll-records` | consumer | **1** | 一次拉一条（长任务） |
| `max.poll.interval.ms` | consumer.properties | **1h** | 单条处理预算 |
| `group-id` | consumer | research-agent-backend | 消费组 |
| `auto-offset-reset` | consumer | earliest | 新组从头读 |

## 四、错误处理（本项目缺的一块，生产必须补）

### 4.1 现状：没配 errorHandler = 无限重试

```text
方法抛异常 → 容器默认 DefaultErrorHandler（Boot 2.8+）：
  重试 10 次（间隔 1s 递增）→ 仍然失败 → 抛给容器 → 【再重投】
  → 结合 max.poll.interval 超时 → Rebalance → 又重投 → 死循环
  → 日志刷屏 + 分区被占死（后续消息全排队）
```

### 4.2 三件套：重试 + 死信 + 兜底

```java
@Configuration
public class KafkaErrorHandlingConfig {

    /** 失败处理：重试 3 次（间隔 1s/2s/4s）→ 仍失败进 DLT（死信主题） */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(1000L, 3));   // 1 秒间隔，重试 3 次
        // 不重试的异常（业务不可恢复）：直接进 DLT
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
```

```text
DLT（Dead Letter Topic）= 死信主题：处理不了的消息进 "原topic.DLT"，人工/脚本处理
好处：① 不再无限重试（分区不被卡死）② 失败消息可追溯 ③ 主链路继续消费
```

### 4.3 手动 ack（需要精确控制时）

```yaml
spring:
  kafka:
    listener:
      ack-mode: manual          # 或 manual_immediate
```

```java
@KafkaListener(topics = "...")
public void handle(String payload, Acknowledgment ack) {
    try {
        doWork(payload);
        ack.acknowledge();        // 成功才提交
    } catch (Exception e) {
        // 不 ack → 重投（或手动发 DLT）
        throw e;
    }
}
```

```text
★ 本项目不需要（record 模式已够：成功自动提交、失败不提交）
什么时候需要 manual：处理里有"部分成功"语义（需要业务判断提交时机）
```

### 4.4 本项目建议（下一步改造）

```text
补一个 KafkaErrorHandlingConfig（DefaultErrorHandler + DLT）：
  · 现在：异常 → 无限重试 → 分区卡死
  · 补后：异常 → 重试 3 次 → 进 .DLT → 分区继续消费
（可在 M5 练习或后续场景里落地）
```

## 五、序列化：String vs JSON（坑最多的地方）

| 方式 | 生产端 | 消费端 | 优点 | 坑 |
|---|---|---|---|---|
| **String**（项目用） | StringSerializer | StringDeserializer | 简单、跨语言 | 消息体是字符串（JSON 要自己解析） |
| JSON | JsonSerializer | JsonDeserializer | 对象直传 | **反序列化失败会死循环重试**（要配 ErrorHandlingDeserializer） |
| Avro | AvroSerializer | AvroDeserializer | Schema 演进 | 需要 Schema Registry |

```text
★ 本项目为什么用 String：
  轻消息原则（只发 taskId）——消息体就是 UUID 字符串，String 最简单、最不易错
  如果发对象（TaskEvent），用 JSON + ErrorHandlingDeserializer（避免毒消息死循环）
```

## 六、并发与顺序（Spring 层怎么配）

```yaml
spring:
  kafka:
    listener:
      concurrency: 1        # 消费线程数（≤分区数；本项目 3 分区 → 最多 3）
```

```text
· concurrency=1：1 个线程吃 3 个分区（项目现状）→ 串行，吞吐低但顺序稳
· concurrency=3：3 个线程各吃 1 个分区 → 吞吐 ×3，仍分区内有序
· concurrency>分区数：多的线程闲着（浪费，Kafka 会分配不到分区）
★ 长任务建议：先扩分区（3→6）+ concurrency=3，再谈吞吐
```

## 七、spring-amqp 预览（M2 环境就绪后补全）

```java
// RabbitMQ 版"申请书"（对照 @KafkaListener 记忆）
@RabbitListener(queues = "task.execution.queue")
public void onTask(String taskId) { ... }

// 声明队列 + 交换机 + 绑定（代替 Kafka 的 NewTopic）
@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "task.execution.queue", durable = "true"),
        exchange = @Exchange(name = "task.exchange", type = ExchangeTypes.DIRECT),
        key = "task.execution"))
public void onTaskWithBinding(String taskId) { ... }
```

```text
对照要点（M2 展开）：
· Kafka：Topic 用 NewTopic Bean 声明；消费靠 group-id 入组
· RabbitMQ：Queue/Exchange/Binding 用注解声明；消费靠队列绑定
· 确认：Kafka=commit offset；RabbitMQ=channel.basicAck
· 死信：Kafka=.DLT 主题；RabbitMQ=DLX 死信交换机
```

## 八、手写练习设计（可选，SP11 风格）

> 想动手时按这个设计敲一遍（半天量）。不敲也能看懂——项目代码已是最好的样例。

```text
新建包 infrastructure/mq/playground/：
① KafkaPlaygroundProducer（发 3 类消息：正常 / 会异常的 / 需要手动 ack 的）
② KafkaPlaygroundListener
   - @KafkaListener(topics="playground", groupId="playground-group") 正常消费
   - 第二个 listener：ack-mode=manual + Acknowledgment 参数（演示手动提交）
   - 第三个：故意抛 IllegalArgumentException → 观察 DefaultErrorHandler 重试 3 次 → 进 .DLT
③ KafkaErrorHandlingConfig（DefaultErrorHandler + DeadLetterPublishingRecoverer）
④ application.yml：spring.kafka.listener.ack-mode=record + concurrency=1
验证：
  灌 10 条 → 看日志（成功/异常/重试/DLT）→ describe 看 LAG → 查 DLT topic 里有没有失败消息
```

## 九、自测题（答案）

```text
Q1 @KafkaListener 背后谁在循环 poll？concurrency 控制的是什么？
Q2 方法抛异常时，默认行为是什么？为什么需要 errorHandler + DLT？
Q3 ack-mode=record 与 manual 的区别？什么场景需要 manual？
Q4 消费端用 JSON 反序列化，遇到"毒消息"（格式错）会发生什么？怎么防？
Q5 concurrency 设 5 但只有 3 个分区，会怎样？
```

### 答案

```text
Q1 每个容器按 concurrency 起 N 个线程，各跑 poll 循环；concurrency 控制消费线程数
   （上限 = 分区数，多了浪费）。
Q2 默认无限重试（DefaultErrorHandler 重试 10 次后仍抛回容器 → 重投 → 死循环）→
   分区被卡死、后续消息全排队。errorHandler+DLT 让失败消息"出去"（进 .DLT），
   主链路继续消费，可追溯可人工处理。
Q3 record：每条处理成功自动提交；manual：代码里显式 ack.acknowledge()。
   需要 manual 的场景：部分成功语义（如一批里按业务判断是否算成功）、
   需要"处理完再统一提交"的精确控制。
Q4 反序列化失败 → 异常 → 重试 → 重投（毒消息死循环，日志刷屏）。
   防：用 ErrorHandlingDeserializer 包装（失败消息进 DLT，不阻塞主链路）。
Q5 2 个线程空闲（分配不到分区）；不会报错但浪费资源。正确做法：concurrency ≤ 分区数。
```

## 十、一句话

```text
@KafkaListener = 申请书；容器（线程池 + poll 循环）= 外包工。
生产级 = 容器配置（concurrency/ack-mode）+ 错误处理（重试→DLT）+ 序列化安全（防毒消息）
——注解写得再短，真正决定可靠性的是这三块配置。
```
